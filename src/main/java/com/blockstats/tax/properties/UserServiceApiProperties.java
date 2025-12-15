package com.blockstats.tax.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "user.api")
@Setter
@Getter
public class UserServiceApiProperties {
    private String userServiceResource;
    private String clientAppId;
}
