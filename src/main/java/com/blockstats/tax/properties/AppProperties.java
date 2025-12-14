package com.blockstats.tax.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tax.app")
@Setter
@Getter
public class AppProperties {
    private String allowedCrossOrigins;
    private String allowedCrossOriginMapping;
    private String trackingServiceTopic;
}
