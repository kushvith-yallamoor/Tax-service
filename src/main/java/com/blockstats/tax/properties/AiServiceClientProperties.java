package com.blockstats.tax.properties;

import com.blockstats.commons.properties.HttpClientProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai.httpclient")
public class AiServiceClientProperties extends HttpClientProperties {
}
