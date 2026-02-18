package com.blockstats.tax.cache;

import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;

import org.apache.commons.collections4.MapUtils;
import org.springframework.stereotype.Service;

import com.blockstats.response.RolePolicy;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class RolePolicySetCacheLoaderService extends CacheLoader<String, Set<RolePolicy>> {

//	private final LoadingCache<String, Map<String, RolePolicy>> rolePolicySetCache;

	@Override
	public Set<RolePolicy> load(String key) {

		log.debug("Loading active subscription plans set for key: {}", key);

		Map<String, RolePolicy> rolePolicyMap = null;
//		try {
//			rolePolicyMap = rolePolicySetCache.get(key);
//		} catch (ExecutionException e) {
//			log.error("Exception when get subscription plans from cache:{}", e.getMessage(), e);
//		}
		TreeSet<RolePolicy> set = new TreeSet<>(Comparator.comparing(RolePolicy::getTxnCount));

		if (MapUtils.isNotEmpty(rolePolicyMap)) {
			set.addAll(rolePolicyMap.values());
		}

		return set;
	}

}