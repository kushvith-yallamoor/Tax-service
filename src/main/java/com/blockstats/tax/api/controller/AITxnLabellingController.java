package com.blockstats.tax.api.controller;

import com.blockstats.commons.api.authorization.bean.UserContext;
import com.blockstats.commons.constants.StatsConstants;
import com.blockstats.commons.stats.StatsCollector;
import com.blockstats.response.AiLabelQuotaResponse;
import com.blockstats.tax.bo.AITxnLabellingBO;
import com.blockstats.tax.repository.AiLabelingPreferenceEntity;
import com.blockstats.tax.repository.AiLabelingPreferenceRepository;
import com.blockstats.tax.service.AITxnLabellingService;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;

import static com.blockstats.commons.constants.HeaderConstants.*;
import static com.blockstats.commons.constants.MDCConstants.*;

@Slf4j
@RestController
@RequestMapping("/v1/aiTxnLabelling")
@AllArgsConstructor
public class AITxnLabellingController {

    private final AITxnLabellingService aiTxnLabellingService;
    private final AiLabelingPreferenceRepository aiLabelingPreferenceRepository;

    @GetMapping
    public ResponseEntity<String> aiTxnLabelling(@RequestHeader HttpHeaders httpHeaders,
                                                 @AuthenticationPrincipal UserContext userContext) {
        this.setMDCInfo(httpHeaders, userContext.getUserId());
        StatsCollector.putInCurrentSession(StatsConstants.EVENT, "AI_TXN_LABELLING");
        AITxnLabellingBO aiTxnLabellingBO = this.buildAITxnLabellingBO(httpHeaders, userContext);
        aiTxnLabellingService.invoke(aiTxnLabellingBO);
        return ResponseEntity.status(HttpStatus.CREATED).body("Initiated AI Labelling for Txns");
    }

    /** How much of the user's plan AI-labeling allowance is left - for the Transactions page's remaining-count display. */
    @GetMapping("/quota")
    public ResponseEntity<AiLabelQuotaResponse> getQuota(@RequestHeader HttpHeaders httpHeaders,
                                                          @AuthenticationPrincipal UserContext userContext) {
        AITxnLabellingBO aiTxnLabellingBO = this.buildAITxnLabellingBO(httpHeaders, userContext);
        return ResponseEntity.ok(aiTxnLabellingService.aiLabelQuota(aiTxnLabellingBO));
    }

    /**
     * Opts this user out of the background scheduler (AITxnLabellingScheduler) - "I don't want
     * this running until I come back and ask for it again." Triggering this endpoint from the
     * Transactions page is still allowed and unaffected; this only gates the no-tab-open path.
     */
    @PostMapping("/pause")
    public ResponseEntity<Void> pause(@AuthenticationPrincipal UserContext userContext) {
        this.setPreference(userContext.getUserId(), false);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/resume")
    public ResponseEntity<Void> resume(@AuthenticationPrincipal UserContext userContext) {
        this.setPreference(userContext.getUserId(), true);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/preference")
    public ResponseEntity<AiLabellingPreferenceResponse> getPreference(@AuthenticationPrincipal UserContext userContext) {
        boolean enabled = this.aiLabelingPreferenceRepository.findByUserId(userContext.getUserId())
                .map(AiLabelingPreferenceEntity::isEnabled)
                .blockOptional()
                .orElse(true);
        AiLabellingPreferenceResponse response = new AiLabellingPreferenceResponse();
        response.setEnabled(enabled);
        return ResponseEntity.ok(response);
    }

    private void setPreference(String userId, boolean enabled) {
        AiLabelingPreferenceEntity entity = this.aiLabelingPreferenceRepository.findByUserId(userId)
                .blockOptional()
                .orElseGet(() -> {
                    AiLabelingPreferenceEntity newEntity = new AiLabelingPreferenceEntity();
                    newEntity.setUserId(userId);
                    return newEntity;
                });
        entity.setEnabled(enabled);
        entity.setUpdatedAt(new Date());
        this.aiLabelingPreferenceRepository.save(entity).block();
    }

    @Getter
    @Setter
    public static class AiLabellingPreferenceResponse {
        private boolean enabled;
    }

    private void setMDCInfo(HttpHeaders headers, String userId) {
        MDC.put(MDC_CORRELATION_ID, headers.getFirst(X_CORRELATION_ID));
        MDC.put(MDC_REQUEST_ID, headers.getFirst(X_REQUEST_ID));
        MDC.put(MDC_CREATED_TIME, headers.getFirst(X_REQUEST_DATE));
        MDC.put(CLIENT_APP_ID, headers.getFirst(X_CLIENT_APP_ID));
        MDC.put(MDC_USER_ID, String.format(MDC_USER_ID_FORMAT, userId));
        MDC.put(MDC_SOURCE, "AI_TXN_LABELLING");
    }

    private AITxnLabellingBO buildAITxnLabellingBO(HttpHeaders headers, UserContext userContext) {
        return AITxnLabellingBO
                .builder()
                .correlationId(headers.getFirst(X_CORRELATION_ID))
                .clientAppId(headers.getFirst(X_CLIENT_APP_ID))
                .requestDate(headers.getFirst(X_REQUEST_DATE))
                .userId(userContext.getUserId())
                .userType(userContext.getUserType())
                .roles(userContext.getRoles())
                .build();
    }
}
