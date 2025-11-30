package com.blockstats.tax.configuration;

import java.util.Collections;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.blockstats.tax.api.filter.HeaderValidationFilter;
import com.blockstats.tax.api.filter.LoggingFilter;
import com.blockstats.tax.api.filter.ResponseHeadersFilter;

@Configuration
public class HttpFilterConfiguration {
	
    public static final String V1_AGGREGATOR_URL = "/v1/*";

    @Bean
    public FilterRegistrationBean createLoggingFilter() {
        FilterRegistrationBean filterRegistrationBean = new FilterRegistrationBean();
        filterRegistrationBean.setFilter(new LoggingFilter());
        filterRegistrationBean.setOrder(1);
        filterRegistrationBean.setName("LOGGING_FILTER");
        filterRegistrationBean.setUrlPatterns(Collections.singletonList(V1_AGGREGATOR_URL));
        return filterRegistrationBean;
    }

    @Bean
    public FilterRegistrationBean createResponseHeadersFilter() {
        FilterRegistrationBean filterRegistrationBean = new FilterRegistrationBean();
        filterRegistrationBean.setFilter(new ResponseHeadersFilter());
        filterRegistrationBean.setOrder(2);
        filterRegistrationBean.setName("RESPONSE_HEADERS_FILTER");
        filterRegistrationBean.setUrlPatterns(Collections.singletonList(V1_AGGREGATOR_URL));
        return filterRegistrationBean;
    }

    @Bean
    public FilterRegistrationBean createHeaderValidationFilter() {
        FilterRegistrationBean filterRegistrationBean = new FilterRegistrationBean();
        filterRegistrationBean.setFilter(new HeaderValidationFilter());
        filterRegistrationBean.setOrder(4);
        filterRegistrationBean.setName("HEADER_VALIDATION_FILTER");
        filterRegistrationBean.setUrlPatterns(Collections.singletonList(V1_AGGREGATOR_URL));
        return filterRegistrationBean;
    }

}