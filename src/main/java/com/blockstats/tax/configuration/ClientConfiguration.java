package com.blockstats.tax.configuration;

import com.blockstats.commons.configuration.builder.HttpConnectionPoolBuilder;
import com.blockstats.commons.configuration.builder.WebClientCreator;
import com.blockstats.tax.properties.TrackingServiceClientProperties;
import com.blockstats.tax.properties.UserServiceClientProperties;
import lombok.AllArgsConstructor;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import static com.blockstats.tax.constant.Constants.*;

@Configuration
@AllArgsConstructor
public class ClientConfiguration {


    @Bean(TRACKING_CLIENT_CONN_POOL)
    public PoolingAsyncClientConnectionManager createTrackingHttpConnectionPool(TrackingServiceClientProperties trackingServiceClientProperties) {
        return HttpConnectionPoolBuilder.createPoolingAsyncClientConnectionManager(trackingServiceClientProperties);
    }

    @Bean(TRACKING_WEB_CLIENT)
    public WebClient createTrackingClient(@Qualifier(TRACKING_CLIENT_CONN_POOL) PoolingAsyncClientConnectionManager connectionManager,
                                          TrackingServiceClientProperties trackingServiceClientProperties) {
        return WebClientCreator.createWebClient(trackingServiceClientProperties, connectionManager);
    }
    
    @Bean(USER_CLIENT_CONN_POOL)
    public PoolingAsyncClientConnectionManager createUserHttpConnectionPool(UserServiceClientProperties userServiceClientProperties) {
        return HttpConnectionPoolBuilder.createPoolingAsyncClientConnectionManager(userServiceClientProperties);
    }

    @Bean(USER_WEB_CLIENT)
    public WebClient createUserClient(@Qualifier(USER_CLIENT_CONN_POOL) PoolingAsyncClientConnectionManager connectionManager,
                                      UserServiceClientProperties userServiceClientProperties) {
        return WebClientCreator.createWebClient(userServiceClientProperties, connectionManager);
    }

}
