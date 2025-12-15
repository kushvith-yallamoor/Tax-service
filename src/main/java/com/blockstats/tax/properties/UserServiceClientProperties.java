package com.blockstats.tax.properties;

import com.blockstats.commons.properties.HttpClientProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "user.httpclient")
public class UserServiceClientProperties extends HttpClientProperties {
}
