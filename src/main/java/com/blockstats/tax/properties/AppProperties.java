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
    // Temporary, product-decision flag: paywalls are off for now, every user gets full access
    // regardless of plan/role - flip back to true to re-enable UserSubscriptionValidationService's
    // txn-count gate and AITxnLabellingService's quota cap, no other code changes needed.
    private boolean planLimitsEnabled = true;
}
