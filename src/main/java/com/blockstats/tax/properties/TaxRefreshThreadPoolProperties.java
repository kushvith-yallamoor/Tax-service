package com.blockstats.tax.properties;

import com.blockstats.commons.properties.ThreadPoolProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tax.refresh.executor")
public class TaxRefreshThreadPoolProperties extends ThreadPoolProperties {
}
