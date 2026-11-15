package com.blockstats.tax.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payment.api")
@Setter
@Getter
public class PaymentServiceApiProperties {
    private String rolePolicyResource;
    private String clientAppId;
    private String userSubscriptionResource;
}
