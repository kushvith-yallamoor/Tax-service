package com.blockstats.tax.delegate;

import com.blockstats.tax.gcp.auth.GCPIdTokenProvider;
import com.blockstats.tax.properties.UserServiceApiProperties;
import com.blockstats.tax.properties.UserServiceClientProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Repository;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.UUID;
import java.util.Date;

import static com.blockstats.commons.constants.HeaderConstants.*;
import static com.blockstats.tax.constant.Constants.USER_CLIENT_CONN_POOL;
import static com.blockstats.tax.constant.Constants.USER_WEB_CLIENT;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;

/**
 * Service-to-service check against user-service's AccountantController#checkAccess (same
 * GCP-ID-token convention as FetchUserPreferenceDelegate) - lets an accountant's read-only
 * request for a client's tax data be verified against the accountant-client-links collection
 * before any of that client's data is returned.
 */
@Repository
@Slf4j
public class AccountantAccessCheckDelegate {

    private final UserServiceApiProperties userServiceApiProperties;
    private final WebClient webClient;
    private final PoolingAsyncClientConnectionManager poolingAsyncClientConnectionManager;
    private final GCPIdTokenProvider gcpIdTokenProvider;
    private final UserServiceClientProperties userServiceClientProperties;

    public AccountantAccessCheckDelegate(UserServiceApiProperties userServiceApiProperties,
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

    public boolean hasActiveAccess(String accountantUserId, String clientUserId) {
        return check(accountantUserId, clientUserId, null);
    }

    public boolean hasFullAccess(String accountantUserId, String clientUserId) {
        return check(accountantUserId, clientUserId, "FULL");
    }

    private boolean check(String accountantUserId, String clientUserId, String minLevel) {
        // user-service's HeaderValidationFilter rejects ANY request (service-to-service included)
        // missing these three headers with a 400 - not specific to this endpoint, so every
        // service-to-service caller into user-service has to send them, same as
        // FetchUserPreferenceDelegate does via the headers it forwards from the original request.
        ResponseEntity<Void> response = webClient
                .get()
                .uri(uriBuilder -> {
                    uriBuilder.path(userServiceApiProperties.getAccountantAccessCheckResource());
                    uriBuilder.queryParam("accountantUserId", accountantUserId);
                    uriBuilder.queryParam("clientUserId", clientUserId);
                    if (minLevel != null) {
                        uriBuilder.queryParam("minLevel", minLevel);
                    }
                    return uriBuilder.build();
                })
                .header(AUTHORIZATION, "Bearer " + gcpIdTokenProvider.getIdToken(userServiceClientProperties.getBaseUrl()))
                .header(X_CORRELATION_ID, UUID.randomUUID().toString())
                .header(X_CLIENT_APP_ID, userServiceApiProperties.getClientAppId())
                .header(X_REQUEST_DATE, new Date().toString())
                .exchangeToMono(clientResponse -> clientResponse.toEntity(Void.class))
                .block();

        log.debug(poolingAsyncClientConnectionManager.getTotalStats().toString());
        return response != null && response.getStatusCode().is2xxSuccessful();
    }
}
