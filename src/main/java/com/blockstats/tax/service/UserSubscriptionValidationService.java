package com.blockstats.tax.service;

import com.blockstats.common.mongo.userdata.delegate.repository.TransactionsRepository;
import com.blockstats.common.tax.bo.TaxBO;
import com.blockstats.commons.api.authorization.bean.UserContext;
import com.blockstats.commons.exception.*;
import com.blockstats.response.RecommendedPlanResponse;
import com.blockstats.response.RolePolicy;
import com.google.common.cache.LoadingCache;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class UserSubscriptionValidationService {

    private final LoadingCache<String, Map<String, RolePolicy>> rolePolicyCache;
    private final LoadingCache<String, Set<RolePolicy>> rolePolicySetCache;
    private final TransactionsRepository transactionsRepository;

    public void validate(UserContext userContext, TaxBO taxBO) {
        String cacheKey = String.format("%s:%s", userContext.getUserType(), taxBO.getCountry()).toUpperCase(Locale.ROOT);
        try {
            // All Roles
            Map<String, RolePolicy> subscriptionPlans = this.rolePolicyCache.get(cacheKey);
            if (MapUtils.isEmpty(subscriptionPlans)) {
                log.error("No subscription plans found for the UserType:{} and Country:{}", userContext.getUserType(), taxBO.getCountry());
                Violation violation = new Violation("country", "Subscription plans not found");
                Set<Violation> violations = new HashSet<>();
                violations.add(violation);
                throw new InvalidRequestException(violations);
            }

            // User transactions count
            long transactionCount = transactionsRepository.countDistinctGroupingIdByUserIdAndTxnDateBetween(userContext.getUserId(), taxBO.getFinancialYear().getStartDate(), taxBO.getFinancialYear().getEndDate()).blockOptional().orElse(0L);
            taxBO.setUserTxnCount(transactionCount);

            // User role check
            Set<String> roles = new HashSet<String>();
			if (CollectionUtils.isEmpty(userContext.getRoles())
					|| CollectionUtils.isEmpty(roles = userContext.getRoles().stream().map(role -> role.replace("ROLE_", ""))
							.filter(role -> !"TU".equals(role)).collect(Collectors.toSet()))) {
				throwInsufficientPlanException(userContext.getUserId(), cacheKey, transactionCount,
						String.format("User has insufficient subscription plan based on their transactions:[%s-%s]",
								userContext.getUserId(), transactionCount));
			}
			
			if (isUpgradeRequired(subscriptionPlans, roles, transactionCount)) {
				RolePolicy rolePolicy = this.getSuggestedPlan(cacheKey, transactionCount).get();
				throwInsufficientPlanException(userContext.getUserId(), cacheKey, transactionCount,
						String.format(
								"User has insufficient subscription plan based on their transactions:[%s-%s-%s-%s]",
								userContext.getUserId(), transactionCount, roles, rolePolicy.getPlanId()));
			}
        } catch (ExecutionException e) {
            log.error("Failed to load subscription plans from cache for key:{}", cacheKey, e);
            throw new RequestProcessingException(e);
        }
    }

    private boolean isUpgradeRequired(Map<String, RolePolicy> subscriptionPlans, Set<String> userRoles, long userTxnCount) {
        return userRoles
                .stream()
                .noneMatch(userRole -> Objects.nonNull(subscriptionPlans.get(userRole))
                        && userTxnCount <= subscriptionPlans.get(userRole).getTxnCount());
    }

    private Optional<RolePolicy> getSuggestedPlan(String cacheKey, long userTxnCount) throws ExecutionException {
        // suggest next suitable plan
        return this.rolePolicySetCache.get(cacheKey).stream().filter(rolePolicy -> userTxnCount <= rolePolicy.getTxnCount()).findFirst();
    }
    
	private void throwInsufficientPlanException(String userId, String cacheKey, long transactionCount,
			String exceptionMessage) throws ExecutionException {
		log.error(exceptionMessage);
		// Check plan suggestion
		RolePolicy rolePolicy = this.getSuggestedPlan(cacheKey, transactionCount).get();
		
		RecommendedPlanResponse recommendedPlan = new RecommendedPlanResponse();
		recommendedPlan.setName(rolePolicy.getName());
		recommendedPlan.setCountry(rolePolicy.getCountry());
		recommendedPlan.setPlanId(rolePolicy.getPlanId());
		recommendedPlan.setUserTxnCount(transactionCount);
		
		throw new InsufficientPlanException(exceptionMessage, recommendedPlan);
	}

}
