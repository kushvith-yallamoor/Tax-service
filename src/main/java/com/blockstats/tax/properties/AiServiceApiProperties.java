package com.blockstats.tax.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai.api")
@Setter
@Getter
public class AiServiceApiProperties {
    private String labelResource;
    private String form1099DaParseResource;
}
