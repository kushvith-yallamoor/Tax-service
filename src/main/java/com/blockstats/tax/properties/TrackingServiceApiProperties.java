package com.blockstats.tax.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tracking.api")
@Setter
@Getter
public class TrackingServiceApiProperties {
    private String trackingServiceResource;
    private String clientAppId;
}
