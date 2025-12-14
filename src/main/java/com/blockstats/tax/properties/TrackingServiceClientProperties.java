package com.blockstats.tax.properties;

import com.blockstats.commons.properties.HttpClientProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tracking.httpclient")
public class TrackingServiceClientProperties extends HttpClientProperties {
}
