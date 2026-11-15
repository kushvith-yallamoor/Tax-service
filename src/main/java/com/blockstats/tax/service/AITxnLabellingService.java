package com.blockstats.tax.service;

import com.blockstats.common.mongo.delegate.TransactionsTxnTypeUpsertDelegate;
import com.blockstats.common.mongo.delegate.dto.TransactionTxnType;
import com.blockstats.common.mongo.delegate.dto.UserContext;
import com.blockstats.common.mongo.userdata.delegate.repository.TransactionsRepository;
import com.blockstats.enums.LocalTxnType;
import com.blockstats.enums.Source;
import com.blockstats.enums.SourceType;
import com.blockstats.response.AiLabelQuotaResponse;
import com.blockstats.response.RolePolicy;
import com.blockstats.tax.bo.AITxnLabellingBO;
import com.blockstats.tax.delegate.AiLabelingDelegate;
import com.blockstats.tax.dto.AiLabelingResponse;
import com.blockstats.tax.properties.AppProperties;
import com.google.common.cache.LoadingCache;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static com.blockstats.enums.SourceType.DEX;
import static com.blockstats.enums.SourceType.DEXEVM;

@Slf4j
@Service
@AllArgsConstructor
public class AITxnLabellingService {

    // Same set tax-service's own cost-basis calculator already treats as non-taxable transfers
    // (AbstractCostBasisCalculator.NON_TAXABLE_LOCAL_TXN_TYPES) - a transaction already resolved
    // as a self-transfer (DEX address match, CEX's own transfer flag, etc.) has nothing left for
    // the AI to decide, so sending it through Gemini was pure wasted spend.
    private static final Set<LocalTxnType> SELF_TRANSFER_TYPES = EnumSet.of(
            LocalTxnType.INTERNALTRANSFER, LocalTxnType.TRANSFER,
            LocalTxnType.TRANSFER_IN, LocalTxnType.TRANSFER_OUT);

    // Chains ai-service actually has RPC/decode config for (ai-service's ChainProperties - see
    // application.yaml there). ai-service's whole pipeline is EVM-specific (method signatures,
    // event logs via Alchemy); a chain not in this list either has no RPC config at all (fails
    // immediately) or isn't EVM to begin with (e.g. Bitcoin, Solana, Tron - no method calls or
    // event logs exist there, so "AI-labeling" them was never meaningful regardless of RPC
    // support). Kept in sync by hand with ai-service's own config; update both when a chain is
    // added there. Excluding unsupported chains here instead of letting them fail per-call keeps
    // the labeling queue (ordered by txnDate DESC) from getting stuck behind transactions that
    // can never succeed.
    private static final List<String> AI_SUPPORTED_SOURCES = List.of("ETHEREUM", "ARBITRUM", "BASE", "ROBINHOODCHAIN");

    // Rows pulled per source per invoke() call - kept small so one trigger (and the per-call
    // Gemini spend behind it) stays bounded even as AI_SUPPORTED_SOURCES grows.
    private static final int ROWS_PER_SOURCE = 2;

    // How many transactions' AI calls run at once per trigger, instead of one at a time. Bounded
    // deliberately - with 4 sources * ROWS_PER_SOURCE this trigger processes at most 8
    // transactions per call anyway, so 4 concurrent already halves the worst-case wall-clock time
    // without opening the door to unbounded concurrent load on ai-service/Gemini/Mongo from a
    // single request.
    private static final int PARALLEL_LABELING_CONCURRENCY = 4;

    // The only country payment-service's subscription plans currently exist for
    // (AppProperties.allowedCountries in payment-service is [USA] today) - AI labeling isn't a
    // country-scoped action the way a tax report is, so there's no user-supplied country to key
    // the plan lookup on. Revisit if/when more countries are onboarded.
    private static final String PLAN_COUNTRY = "USA";

    private final TransactionsRepository transactionsRepository;
    private final TransactionsTxnTypeUpsertDelegate transactionsTxnTypeUpsertDelegate;
    private final AiLabelingDelegate aiLabelingDelegate;
    private final LoadingCache<String, Map<String, RolePolicy>> rolePolicyCache;
    private final AppProperties appProperties;

    public void invoke(AITxnLabellingBO aITxnLabellingBO) {
        long remainingQuota = this.aiLabelQuota(aITxnLabellingBO).getRemaining();
        this.processBacklog(aITxnLabellingBO.getUserId(), remainingQuota);
    }

    /**
     * The actual per-user labeling work, shared by the per-request path (invoke(BO), triggered
     * from the Transactions page) and the background scheduler (AITxnLabellingScheduler, which
     * has no per-request BO/roles - it resolves remainingQuota itself via
     * FetchUserSubscriptionDelegate instead of the JWT-derived role lookup aiLabelQuota() does).
     */
    public void processBacklog(String userId, long remainingQuota) {
        if (remainingQuota <= 0) {
            return;
        }

        // Queries each supported source separately (ROWS_PER_SOURCE each) instead of one query
        // ordered by txnDate DESC across all of them combined. A combined query's top-N page can
        // be monopolized indefinitely by whichever source has the highest transaction volume near
        // the present (e.g. a chain with frequent small transfers) - every other source's backlog,
        // however old, never reaches the front of a globally date-sorted queue. Querying per
        // source and merging gives every eligible source a slice of each batch.
        Flux.fromIterable(AI_SUPPORTED_SOURCES)
                .concatMap(source -> this.transactionsRepository
                        .findRetryableByUserIdAndSourceAndSourceTypeInAndLocalTxnTypeNotInOrderByTxnDateDesc(
                                userId,
                                source,
                                List.of(DEX.value(), DEXEVM.value()),
                                List.copyOf(SELF_TRANSFER_TYPES),
                                PageRequest.of(0, ROWS_PER_SOURCE)))
                // The query already caps rows per source, but quota can be smaller than the total
                // (e.g. a user with 1 label left on their plan) - cap the actual AI calls, not just
                // the DB read, to whatever's left.
                .take(remainingQuota)
                // Was .subscribe(entity -> { ...blocking AI call...}) - a plain subscribe runs its
                // callback for each emitted item strictly one at a time, so every transaction's AI
                // round-trip (and, for Robinhoodchain, an extra Blockscout round-trip) waited for
                // the previous one to finish completely. Up to ROWS_PER_SOURCE * 4 sources worth of
                // several-second AI calls in sequence is exactly the multi-second-to-tens-of-
                // seconds latency users were seeing on a single trigger. flatMap with a bounded
                // concurrency runs that many transactions' AI calls at once instead - bounded so
                // one user's trigger can't flood ai-service/Gemini/Mongo with unlimited concurrent
                // calls, same spirit as the deliberately small ROWS_PER_SOURCE cap.
                .flatMap(transactionEntity -> Mono.fromRunnable(() -> this.processOneTransaction(transactionEntity))
                        .subscribeOn(Schedulers.boundedElastic()), PARALLEL_LABELING_CONCURRENCY)
                .subscribe();
    }

    private void processOneTransaction(com.blockstats.common.mongo.userdata.delegate.entity.TransactionEntity transactionEntity) {
        // The retryable query admits both never-attempted (blank aiTxnType) and
        // previously-failed (aiTxnType == UNRESOLVED) rows - guard against blank here
        // too, not against UNRESOLVED, since UNRESOLVED rows are exactly the ones this
        // retry path exists to give another attempt.
        boolean eligible = StringUtils.isBlank(transactionEntity.getAiTxnType())
                || UNRESOLVED_LABEL.equals(transactionEntity.getAiTxnType());
        if (!eligible || StringUtils.isBlank(transactionEntity.getTxnId())) {
            return;
        }
        AiLabelingResponse aiResponse = this.resolveLabel(transactionEntity.getSource(), transactionEntity.getTxnId());
        String aiTxnType = aiResponse != null ? aiResponse.getTransactionLabel() : null;
        if (StringUtils.isBlank(aiTxnType)) {
            // A failed attempt (e.g. a source ai-service has no chain config for,
            // like Robinhoodchain) previously left aiTxnType null - which looks
            // identical to "never attempted" to this very query, so the exact same
            // failing transactions got retried on every future invocation forever.
            // Worse, since this query orders by txnDate DESC, a handful of
            // permanently-unlabelable recent transactions could block this user's
            // queue from ever reaching genuinely labelable older ones. Mark it so
            // it's never picked up again instead of silently leaving it null.
            this.markUnresolved(transactionEntity);
            return;
        }
        UserContext userContext =
                UserContext
                        .builder()
                        .userId(transactionEntity.getUserId())
                        .walletId(transactionEntity.getWalletId())
                        // Was SourceType.valueOf(transactionEntity.getSource()) -
                        // getSource() returns the chain/exchange name (e.g.
                        // "ROBINHOODCHAIN"), not a SourceType value (DEX/DEXEVM/
                        // CEX), so this threw IllegalArgumentException on every
                        // single call, silently killing the whole reactive stream
                        // mid-subscription. No AI label has ever actually
                        // persisted through this path as a result.
                        .sourceType(SourceType.valueOf(transactionEntity.getSourceType()))
                        .source(Source.valueOf(transactionEntity.getSource()))
                        .build();
        TransactionTxnType transactionTxnType =
                TransactionTxnType
                        .builder()
                        // TransactionsTxnTypeUpsertBuilder matches on txnId as
                        // part of its criteria - without this, the criteria's
                        // txnId.is(null) can never match a real transaction
                        // (every one has a real txnId), so this was silently
                        // upserting a phantom document instead of updating the
                        // actual transaction's aiTxnType.
                        .txnId(transactionEntity.getTxnId())
                        .aiTxnType(aiTxnType)
                        .aiTxnReason(aiResponse.getReason())
                        .taxable(this.parseTaxable(aiResponse.getTaxable()))
                        .build();
        log.info("AI_LABEL_WRITE userId={} source={} sourceType={} txnId={} aiTxnType={}",
                transactionEntity.getUserId(), transactionEntity.getSource(), transactionEntity.getSourceType(),
                transactionEntity.getTxnId(), aiTxnType);
        transactionsTxnTypeUpsertDelegate.upsert(userContext, List.of(transactionTxnType));
    }

    /**
     * How much of this user's plan AI-labeling allowance is left this period, based on their
     * plan's aiTxnLabelCount (e.g. Free=0, Basic=1000, Plus=2500, Pro=10000) minus how many
     * DEX/DEXEVM transactions already have an aiTxnType set. Also the backing call for the
     * user-facing quota endpoint (AITxnLabellingController.getQuota), not just the internal gate.
     * Fails OPEN (a small default rather than 0 remaining) on any lookup error - an outage in the
     * plan cache shouldn't silently stop AI labeling for every user, the way
     * UserSubscriptionValidationService's fail-closed behavior is appropriate for a single
     * user-triggered tax-report request but isn't here for a background process touching every
     * user (or a read-only "how much do I have left" display call).
     */
    // Interim flat allowance applied to every signed-in user while plan limits are disabled
    // (payment gateway not yet wired up) - replaces the old unconditional-unlimited behavior,
    // which the frontend had no way to distinguish from a real large number (shown literally as
    // "2147483647/2147483647 AI labels left"). Revisit once billing/plan limits are back on.
    private static final int FLAT_QUOTA_WHILE_PLAN_LIMITS_DISABLED = 5000;

    public AiLabelQuotaResponse aiLabelQuota(AITxnLabellingBO aITxnLabellingBO) {
        if (!appProperties.isPlanLimitsEnabled()) {
            long pending = this.transactionsRepository
                    .countRetryableByUserIdAndSourceInAndSourceTypeInAndLocalTxnTypeNotIn(
                            aITxnLabellingBO.getUserId(), AI_SUPPORTED_SOURCES, List.of(DEX.value(), DEXEVM.value()), List.copyOf(SELF_TRANSFER_TYPES))
                    .blockOptional()
                    .orElse(0L);
            long used = this.transactionsRepository
                    .countByUserIdAndSourceTypeInAndAiTxnTypeIsNotNull(aITxnLabellingBO.getUserId(), List.of(DEX.value(), DEXEVM.value()))
                    .blockOptional()
                    .orElse(0L);
            AiLabelQuotaResponse quota = new AiLabelQuotaResponse();
            quota.setPlanName("Pro");
            quota.setTotal(FLAT_QUOTA_WHILE_PLAN_LIMITS_DISABLED);
            quota.setUsed((int) used);
            quota.setRemaining((int) Math.max(0, FLAT_QUOTA_WHILE_PLAN_LIMITS_DISABLED - used));
            quota.setPendingCount((int) pending);
            return quota;
        }
        try {
            String cacheKey = String.format("%s:%s", aITxnLabellingBO.getUserType(), PLAN_COUNTRY).toUpperCase(Locale.ROOT);
            Map<String, RolePolicy> plansByRole = this.rolePolicyCache.get(cacheKey);

            Set<String> roles = CollectionUtils.isEmpty(aITxnLabellingBO.getRoles())
                    ? Set.of()
                    : aITxnLabellingBO.getRoles().stream()
                            .map(role -> role.replace("ROLE_", ""))
                            .filter(role -> !"TU".equals(role))
                            .collect(Collectors.toSet());

            RolePolicy rolePolicy = roles.stream()
                    .map(plansByRole::get)
                    .filter(java.util.Objects::nonNull)
                    .findFirst()
                    .orElse(null);

            AiLabelQuotaResponse quota = new AiLabelQuotaResponse();
            if (rolePolicy == null || rolePolicy.getAiTxnLabelCount() <= 0) {
                quota.setPlanName(rolePolicy != null ? rolePolicy.getName() : null);
                quota.setTotal(0);
                quota.setUsed(0);
                quota.setRemaining(0);
                return quota;
            }

            long used = this.transactionsRepository
                    .countByUserIdAndSourceTypeInAndAiTxnTypeIsNotNull(aITxnLabellingBO.getUserId(), List.of(DEX.value(), DEXEVM.value()))
                    .blockOptional()
                    .orElse(0L);
            long pending = this.transactionsRepository
                    .countRetryableByUserIdAndSourceInAndSourceTypeInAndLocalTxnTypeNotIn(
                            aITxnLabellingBO.getUserId(), AI_SUPPORTED_SOURCES, List.of(DEX.value(), DEXEVM.value()), List.copyOf(SELF_TRANSFER_TYPES))
                    .blockOptional()
                    .orElse(0L);

            quota.setPlanName(rolePolicy.getName());
            quota.setTotal(rolePolicy.getAiTxnLabelCount());
            quota.setUsed((int) used);
            quota.setRemaining((int) Math.max(0, rolePolicy.getAiTxnLabelCount() - used));
            quota.setPendingCount((int) pending);
            return quota;
        } catch (ExecutionException e) {
            log.error("Failed to load subscription plans for userType:{}, defaulting to a small AI-labeling allowance",
                    aITxnLabellingBO.getUserType(), e);
            AiLabelQuotaResponse quota = new AiLabelQuotaResponse();
            quota.setRemaining(5);
            return quota;
        }
    }

    // Sentinel written when AI labeling genuinely cannot resolve this transaction (e.g. an
    // unsupported chain) - distinct from a blank aiTxnType, which this query's own filter
    // interprets as "never attempted" and would retry forever.
    private static final String UNRESOLVED_LABEL = "UNRESOLVED";

    private void markUnresolved(com.blockstats.common.mongo.userdata.delegate.entity.TransactionEntity transactionEntity) {
        UserContext userContext =
                UserContext
                        .builder()
                        .userId(transactionEntity.getUserId())
                        .walletId(transactionEntity.getWalletId())
                        .sourceType(SourceType.valueOf(transactionEntity.getSourceType()))
                        .source(Source.valueOf(transactionEntity.getSource()))
                        .build();
        TransactionTxnType transactionTxnType =
                TransactionTxnType
                        .builder()
                        .txnId(transactionEntity.getTxnId())
                        .aiTxnType(UNRESOLVED_LABEL)
                        .build();
        transactionsTxnTypeUpsertDelegate.upsert(userContext, List.of(transactionTxnType));
    }

    // AI returns taxable as "True"/"False" text (ai-service's LabelingResponse.taxable), not a
    // real boolean - only "true"/"false" (any case) is treated as a usable answer; anything else
    // (blank, "Unknown", a stray sentence) returns null so the caller leaves the transaction's
    // existing taxable value untouched rather than overwriting it with a guess.
    private Boolean parseTaxable(String taxable) {
        if ("true".equalsIgnoreCase(taxable)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(taxable)) {
            return Boolean.FALSE;
        }
        return null;
    }

    private AiLabelingResponse resolveLabel(String chainName, String txHash) {
        try {
            return aiLabelingDelegate.getLabel(chainName, txHash);
        } catch (Exception e) {
            log.error("AI Labeling failed for txnId:{}, chain:{}", txHash, chainName, e);
            return null;
        }
    }

    /**
     * Same quota math as aiLabelQuota(), but keyed off an already-fetched aiTxnLabelCount (the
     * scheduler gets this from FetchUserSubscriptionDelegate, not a JWT) instead of resolving it
     * from AITxnLabellingBO's roles. aiTxnLabelCount <= 0 (no plan, or a plan with zero AI-label
     * allowance) returns 0 remaining, the same as aiLabelQuota()'s own "no match" branch.
     */
    public long resolveRemainingQuota(String userId, long aiTxnLabelCount) {
        long used = this.transactionsRepository
                .countByUserIdAndSourceTypeInAndAiTxnTypeIsNotNull(userId, List.of(DEX.value(), DEXEVM.value()))
                .blockOptional()
                .orElse(0L);
        if (!appProperties.isPlanLimitsEnabled()) {
            return Math.max(0, FLAT_QUOTA_WHILE_PLAN_LIMITS_DISABLED - used);
        }
        if (aiTxnLabelCount <= 0) {
            return 0;
        }
        return Math.max(0, aiTxnLabelCount - used);
    }

    /**
     * Every distinct userId with at least one labelable transaction still stuck null/UNRESOLVED -
     * the background scheduler's worklist.
     */
    public reactor.core.publisher.Flux<String> findUsersWithPendingBacklog() {
        return this.transactionsRepository
                .findDistinctUserIdsWithRetryableBacklog(AI_SUPPORTED_SOURCES, List.of(DEX.value(), DEXEVM.value()), List.copyOf(SELF_TRANSFER_TYPES))
                .map(doc -> doc.get("_id"))
                .filter(java.util.Objects::nonNull)
                .map(Object::toString);
    }

}
