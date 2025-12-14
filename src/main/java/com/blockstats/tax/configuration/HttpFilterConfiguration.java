package com.blockstats.tax.configuration;

import com.blockstats.commons.api.filter.RequestAndResponseLoggingFilter;
import com.blockstats.commons.api.filter.ResponseHeadersFilter;
import com.blockstats.tax.api.filter.HeaderValidationFilter;
import com.blockstats.tax.api.filter.StatsLoggingFilter;
import com.blockstats.tax.stats.StatsLogger;
import lombok.AllArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static com.blockstats.commons.constants.AppConstants.*;

@Configuration
@AllArgsConstructor
public class HttpFilterConfiguration {

    public static final String V1_PORTFOLIO_URL = "/v1/*";
    private final StatsLogger statsLogger;

    @Bean
    public FilterRegistrationBean<StatsLoggingFilter> createStatsLoggingFilter() {
        FilterRegistrationBean<StatsLoggingFilter> filterRegistrationBean = new FilterRegistrationBean<>();
        filterRegistrationBean.setFilter(new StatsLoggingFilter(statsLogger));
        filterRegistrationBean.setOrder(1);
        filterRegistrationBean.setName(STATS_LOGGING_FILTER);
        filterRegistrationBean.setUrlPatterns(List.of(V1_PORTFOLIO_URL));
        filterRegistrationBean.addUrlPatterns();
        return filterRegistrationBean;
    }

    @Bean
    public FilterRegistrationBean<RequestAndResponseLoggingFilter> createLoggingFilter() {
        FilterRegistrationBean<RequestAndResponseLoggingFilter> filterRegistrationBean = new FilterRegistrationBean<>();
        filterRegistrationBean.setFilter(new RequestAndResponseLoggingFilter());
        filterRegistrationBean.setOrder(2);
        filterRegistrationBean.setName(REQUEST_RESPONSE_LOGGING_FILTER);
        filterRegistrationBean.setUrlPatterns(List.of(V1_PORTFOLIO_URL));
        filterRegistrationBean.addUrlPatterns();
        return filterRegistrationBean;
    }

    @Bean
    public FilterRegistrationBean<ResponseHeadersFilter> createResponseHeadersFilter() {
        FilterRegistrationBean<ResponseHeadersFilter> filterRegistrationBean = new FilterRegistrationBean<>();
        filterRegistrationBean.setFilter(new ResponseHeadersFilter());
        filterRegistrationBean.setOrder(3);
        filterRegistrationBean.setName(RESPONSE_HEADERS_FILTER);
        filterRegistrationBean.setUrlPatterns(List.of(V1_PORTFOLIO_URL));
        return filterRegistrationBean;
    }

    @Bean
    public FilterRegistrationBean<HeaderValidationFilter> createHeaderValidationFilter() {
        FilterRegistrationBean<HeaderValidationFilter> filterRegistrationBean = new FilterRegistrationBean<>();
        filterRegistrationBean.setFilter(new HeaderValidationFilter());
        filterRegistrationBean.setOrder(4);
        filterRegistrationBean.setName(HEADER_VALIDATION_FILTER);
        filterRegistrationBean.setUrlPatterns(List.of(V1_PORTFOLIO_URL));
        return filterRegistrationBean;
    }

}