package com.blockstats.tax.cache;

import com.blockstats.response.RolePolicy;
import com.blockstats.response.RolePolicyResponse;
import com.blockstats.tax.bo.PaymentBO;
import com.blockstats.tax.delegate.FetchRolePolicyDelegate;
import com.google.common.cache.CacheLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RolePolicyCacheLoaderService extends CacheLoader<String, Map<String, RolePolicy>> {

    private final FetchRolePolicyDelegate fetchSubscriptionPlansDelegate;

    @Override
    public Map<String, RolePolicy> load(String key) {
        String[] parts = key.split(":");
        long userType = Long.parseLong(parts[0]);
        String country = parts[1];

        log.debug("Loading active subscription plans for Type: {} and Country: {}", userType, country);

        RolePolicyResponse rolePolicyResponse = fetchSubscriptionPlansDelegate.execute(PaymentBO.builder().userType(userType).build());

        return Objects.nonNull(rolePolicyResponse) ? rolePolicyResponse.getRolePolicies().stream().collect(Collectors.toMap(RolePolicy::getRole, Function.identity(), (existing, replacement) -> replacement)) : Map.of();
    }


}