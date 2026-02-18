package com.blockstats.tax.configuration;

import static com.blockstats.tax.constant.Constants.ROLE_POLICY_CACHE;
import static com.blockstats.tax.constant.Constants.ROLE_POLICY_SET_CACHE;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.blockstats.response.RolePolicy;
import com.blockstats.tax.cache.RolePolicyCacheLoaderService;
import com.blockstats.tax.cache.RolePolicySetCacheLoaderService;
import com.blockstats.tax.properties.RolePolicyCacheProperties;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@AllArgsConstructor
public class RolePolicyCacheConfiguration {

    private final RolePolicyCacheProperties rolePolicyCacheProperties;

    @Bean(ROLE_POLICY_CACHE)
    public LoadingCache<String, Map<String, RolePolicy>> rolePolicyCache(RolePolicyCacheLoaderService rolePolicyCacheLoaderService) {
        log.debug("Initializing SubscriptionEntity Plans Cache Bean");
        ExecutorService executorService = Executors.newSingleThreadExecutor();

        return CacheBuilder.newBuilder()
                .refreshAfterWrite(rolePolicyCacheProperties.getRefreshTime(), TimeUnit.MILLISECONDS)
                .build(CacheLoader.asyncReloading(rolePolicyCacheLoaderService, executorService));
    }
    
    @Bean(ROLE_POLICY_SET_CACHE)
    public LoadingCache<String, Set<RolePolicy>> rolePolicySetCache(RolePolicySetCacheLoaderService rolePolicySetCacheLoaderService) {
        log.debug("Initializing SubscriptionEntity Plans Set Cache Bean");
        ExecutorService executorService = Executors.newSingleThreadExecutor();

        return CacheBuilder.newBuilder()
                .refreshAfterWrite(rolePolicyCacheProperties.getRefreshTime(), TimeUnit.MILLISECONDS)
                .build(CacheLoader.asyncReloading(rolePolicySetCacheLoaderService, executorService));
    }

}