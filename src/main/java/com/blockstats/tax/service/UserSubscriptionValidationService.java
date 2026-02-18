package com.blockstats.tax.service;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.springframework.stereotype.Service;

import com.blockstats.common.mongo.userdata.delegate.repository.TransactionsRepository;
import com.blockstats.common.tax.bo.TaxBO;
import com.blockstats.commons.api.authorization.bean.UserContext;
import com.blockstats.commons.exception.InsufficientPlanException;
import com.blockstats.commons.exception.InvalidRequestException;
import com.blockstats.commons.exception.Violation;
import com.blockstats.response.RolePolicy;
import com.google.common.cache.LoadingCache;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
            
            // User role
            Set<String> roles = userContext.getRoles().stream().map(role -> role.replace("ROLE_", "")).filter(role -> !"TU".equals(role)).collect(Collectors.toSet());
            if(CollectionUtils.isEmpty(roles)) {
            	log.error("No subscription plans found for the UserType:{} and Country:{}", userContext.getUserType(), taxBO.getCountry());
            	
            	// Check plan suggestion
            	Optional<RolePolicy> suggested = suggestPlan(subscriptionPlans, rolePolicySetCache.get(cacheKey), roles, transactionCount);
            	throw new InsufficientPlanException(suggested.get().getName(), suggested.get().getPlanId(),
            			suggested.get().getCountry(),
						String.format("User has insufficient subscription plan based on their transactions:[%s-%s]",
								userContext.getUserId(), transactionCount));
            }
            
            // Check plan suggestion
            Optional<RolePolicy> suggested = suggestPlan(subscriptionPlans, rolePolicySetCache.get(cacheKey), roles, transactionCount);
            
			if (suggested.isPresent()) {
				RolePolicy rolePolicy = suggested.get();
				throw new InsufficientPlanException(rolePolicy.getName(), rolePolicy.getPlanId(),
						rolePolicy.getCountry(),
						String.format("User has insufficient subscription plan based on their transactions:[%s-%s]",
								userContext.getUserId(), transactionCount));
			}
        } catch (ExecutionException e) {
            log.error("Failed to load subscription plans from cache for key:{}", cacheKey, e);
            throw new RuntimeException("Exception when get subscription plans from cache:" + cacheKey, e);
        }
    }
    
	public Optional<RolePolicy> suggestPlan(Map<String, RolePolicy> subscriptionPlans, Set<RolePolicy> policies,
			Set<String> roles, long userTxnCount) {

		// Check user role
		for (String role : roles) {
			RolePolicy rolePolicy = subscriptionPlans.get(role);
			if (rolePolicy != null && userTxnCount <= rolePolicy.getTxnCount()) {
				return Optional.empty(); // no upgrade needed
			}
		}

		// Temporary sorting until fix cache issue
		policies = subscriptionPlans.values()
                .stream()
                .sorted(Comparator.comparingLong(RolePolicy::getTxnCount))
                .collect(Collectors.toSet());
        
		// suggest next suitable plan
		return policies.stream().filter(p -> userTxnCount <= p.getTxnCount()).findFirst();
	}

}
