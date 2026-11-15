package com.blockstats.tax;

import com.blockstats.tax.properties.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoReactiveDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoReactiveRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoReactiveAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.mongodb.repository.config.EnableReactiveMongoRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import static com.blockstats.common.mongo.userdata.constants.UserdataMongoBeanNames.USERDATA_REACTIVE_MONGO_TEMPLATE;

@SpringBootApplication(scanBasePackages = {"com.blockstats"},
        exclude = {MongoAutoConfiguration.class, MongoDataAutoConfiguration.class,
                MongoRepositoriesAutoConfiguration.class, MongoReactiveAutoConfiguration.class,
                MongoReactiveDataAutoConfiguration.class, MongoReactiveRepositoriesAutoConfiguration.class
        })
@EnableReactiveMongoRepositories(basePackages = {"com.blockstats.common.tax.delegate.mongo.repository", "com.blockstats.tax.repository"}, reactiveMongoTemplateRef = USERDATA_REACTIVE_MONGO_TEMPLATE)
@EnableConfigurationProperties({AppProperties.class, TrackingServiceApiProperties.class, TrackingServiceClientProperties.class,
        UserServiceClientProperties.class, UserServiceApiProperties.class,
        PaymentServiceApiProperties.class, PaymentServiceClientProperties.class, RolePolicyCacheProperties.class,
        TaxRefreshThreadPoolProperties.class, AiServiceClientProperties.class, AiServiceApiProperties.class})
@EnableAsync
@EnableScheduling
public class TaxServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TaxServiceApplication.class, args);
    }

}
