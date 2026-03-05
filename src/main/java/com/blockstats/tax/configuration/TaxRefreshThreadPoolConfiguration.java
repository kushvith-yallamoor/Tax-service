package com.blockstats.tax.configuration;

import static com.blockstats.tax.constant.Constants.TAX_REFRESH_THREAD_POOL;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.blockstats.commons.configuration.builder.ThreadPoolExecutorBuilder;
import com.blockstats.tax.properties.TaxRefreshThreadPoolProperties;

@Configuration
public class TaxRefreshThreadPoolConfiguration {

    @Bean(TAX_REFRESH_THREAD_POOL)
    public Executor buildTaxRefreshThreadPoolExecutor(TaxRefreshThreadPoolProperties taxRefreshThreadPoolProperties) {
        return ThreadPoolExecutorBuilder.createExecutor(taxRefreshThreadPoolProperties);
    }

}
