package com.blockstats.tax.configuration;

import com.blockstats.tax.api.filter.AuthorizationRolesSetupFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import static org.springframework.security.config.http.SessionCreationPolicy.STATELESS;

@Configuration
@EnableMethodSecurity
public class ApplicationSecurityConfiguration {

    @Bean
    public AuthorizationRolesSetupFilter authorizationRolesSetupFilter() {
        return new AuthorizationRolesSetupFilter();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AuthorizationRolesSetupFilter authorizationRolesSetupFilter) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(httpSecuritySessionManagementConfigurer -> httpSecuritySessionManagementConfigurer.sessionCreationPolicy(STATELESS))
                .authorizeHttpRequests(authorizeRequests ->
                        authorizeRequests
                                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                                .anyRequest().authenticated())
                .addFilterBefore(authorizationRolesSetupFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }


}
