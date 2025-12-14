package com.blockstats.tax.configuration;

import com.blockstats.tax.properties.AppProperties;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import static org.springframework.http.HttpMethod.*;

@Configuration
@AllArgsConstructor
public class AppConfiguration {

    private final AppProperties appProperties;

    @Bean
    public WebMvcConfigurer buildWebMvcConfigurer() {
        String[] origins = appProperties.getAllowedCrossOrigins().split(",");
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping(appProperties.getAllowedCrossOriginMapping())
                        .allowedMethods(GET.name(), POST.name(), PUT.name(), DELETE.name(), OPTIONS.name())
                        .allowedOrigins(origins)
                        .allowedHeaders("*");
            }

            @Override
            public void addInterceptors(final InterceptorRegistry interceptorRegistry) {
//                interceptorRegistry.addInterceptor(new UserAuthorizationInterceptor());
            }
        };
    }

}
