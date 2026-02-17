package com.blockstats.tax.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "cache.subscription-plans")
public class RolePolicyCacheProperties {
    // Default to 5 minutes if not set in yaml
    private long refreshTime = 300000;
}