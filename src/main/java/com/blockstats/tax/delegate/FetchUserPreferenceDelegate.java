package com.blockstats.tax.delegate;

import com.blockstats.common.tax.bo.TaxBO;
import com.blockstats.commons.exception.NoDataFoundException;
import com.blockstats.commons.exception.RequestProcessingException;
import com.blockstats.commons.util.JsonParser;
import com.blockstats.response.UserPreferencesResponse;
import com.blockstats.tax.gcp.auth.GCPIdTokenProvider;
import com.blockstats.tax.properties.UserServiceApiProperties;
import com.blockstats.tax.properties.UserServiceClientProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Repository;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Map;

import static com.blockstats.commons.constants.HeaderConstants.*;
import static com.blockstats.tax.constant.Constants.USER_CLIENT_CONN_POOL;
import static com.blockstats.tax.constant.Constants.USER_WEB_CLIENT;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;

@Repository
@Slf4j
public class FetchUserPreferenceDelegate {
    private final com.blockstats.tax.properties.UserServiceApiProperties userServiceApiProperties;
    private final WebClient webClient;
    private final PoolingAsyncClientConnectionManager poolingAsyncClientConnectionManager;
    private final GCPIdTokenProvider gcpIdTokenProvider;
    private final com.blockstats.tax.properties.UserServiceClientProperties userServiceClientProperties;

    public FetchUserPreferenceDelegate(UserServiceApiProperties userServiceApiProperties,
                                       @Qualifier(USER_WEB_CLIENT) WebClient webClient,
                                       @Qualifier(USER_CLIENT_CONN_POOL) PoolingAsyncClientConnectionManager poolingAsyncClientConnectionManager,
                                       GCPIdTokenProvider gcpIdTokenProvider,
                                       UserServiceClientProperties userServiceClientProperties) {
        this.userServiceApiProperties = userServiceApiProperties;
        this.webClient = webClient;
        this.poolingAsyncClientConnectionManager = poolingAsyncClientConnectionManager;
        this.gcpIdTokenProvider = gcpIdTokenProvider;
        this.userServiceClientProperties = userServiceClientProperties;
    }

    public UserPreferencesResponse execute(TaxBO taxBO) {
        log.debug("Fetch Request Status URL invoked : {}", userServiceApiProperties.getUserServiceResource());

        Map<String, String> queryParams = new HashMap<>();
        queryParams.put("userId", taxBO.getUserId());
        ResponseEntity<String> response =
                webClient
                        .get()
                        .uri(uriBuilder -> {
                            uriBuilder.path(userServiceApiProperties.getUserServiceResource());
                            queryParams.forEach(uriBuilder::queryParam);
                            return uriBuilder.build();
                        })
                        .header(AUTHORIZATION, "Bearer " + gcpIdTokenProvider.getIdToken(userServiceClientProperties.getBaseUrl()))
                        .header(X_CORRELATION_ID, taxBO.getCorrelationId())
                        .header(X_CLIENT_APP_ID, userServiceApiProperties.getClientAppId())
                        .header(X_REQUEST_DATE, taxBO.getRequestDate())
                        .exchangeToMono(clientResponse -> clientResponse.toEntity(String.class))
                        .block();

        log.debug("Fetch Request Status API returned with Response Code:{}", response.getStatusCode().value());
        log.debug(poolingAsyncClientConnectionManager.getTotalStats().toString());
        return this.processResponse(response);
    }

    private UserPreferencesResponse processResponse(ResponseEntity<String> response) {
        if (response.hasBody() && response.getStatusCode().value() != NOT_FOUND.value()) {
            String responseBody = response.getBody();
            if (OK.value() == response.getStatusCode().value()) {
                log.debug("Fetch Request Status Response body:{}", responseBody);
                return JsonParser.deSerialize(responseBody, UserPreferencesResponse.class);
            } else {
                log.error("Fetch Request Status API returned with error code: {} and body: {}", response.getStatusCode().value(), response.getBody());
                throw new RequestProcessingException("Unable to fetch Fetch Request Status Data.");
            }
        } else {
            if (NOT_FOUND.value() == response.getStatusCode().value()) {
                log.error("Response Status:{}", response.getStatusCode().value());
                throw new NoDataFoundException("User Preference Not Found.");
            }
            log.error("Fetch Request Status API returned without body");
            throw new RequestProcessingException("Unable to fetch Fetch Request Status Data.");
        }
    }
}
