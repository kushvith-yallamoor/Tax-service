package com.blockstats.tax.delegate;

import static com.blockstats.commons.constants.HeaderConstants.X_CLIENT_APP_ID;
import static com.blockstats.commons.constants.HeaderConstants.X_CORRELATION_ID;
import static com.blockstats.commons.constants.HeaderConstants.X_REQUEST_DATE;
import static com.blockstats.tax.constant.Constants.PAYMENT_CLIENT_CONN_POOL;
import static com.blockstats.tax.constant.Constants.PAYMENT_WEB_CLIENT;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;

import java.util.HashMap;
import java.util.Map;

import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Repository;
import org.springframework.web.reactive.function.client.WebClient;

import com.blockstats.common.tax.bo.TaxBO;
import com.blockstats.commons.exception.NoDataFoundException;
import com.blockstats.commons.exception.RequestProcessingException;
import com.blockstats.commons.util.JsonParser;
import com.blockstats.response.RolePolicyResponse;
import com.blockstats.tax.bo.PaymentBO;
import com.blockstats.tax.gcp.auth.GCPIdTokenProvider;
import com.blockstats.tax.properties.PaymentServiceApiProperties;
import com.blockstats.tax.properties.PaymentServiceClientProperties;

import lombok.extern.slf4j.Slf4j;

@Repository
@Slf4j
public class FetchRolePolicyDelegate {
	
    private final PaymentServiceApiProperties paymentServiceApiProperties;
    private final WebClient webClient;
    private final PoolingAsyncClientConnectionManager poolingAsyncClientConnectionManager;
    private final GCPIdTokenProvider gcpIdTokenProvider;
    private final PaymentServiceClientProperties paymentServiceClientProperties;

    public FetchRolePolicyDelegate(PaymentServiceApiProperties paymentServiceApiProperties,
                                       @Qualifier(PAYMENT_WEB_CLIENT) WebClient webClient,
                                       @Qualifier(PAYMENT_CLIENT_CONN_POOL) PoolingAsyncClientConnectionManager poolingAsyncClientConnectionManager,
                                       GCPIdTokenProvider gcpIdTokenProvider,
                                       PaymentServiceClientProperties paymentServiceClientProperties) {
        this.paymentServiceApiProperties = paymentServiceApiProperties;
        this.webClient = webClient;
        this.poolingAsyncClientConnectionManager = poolingAsyncClientConnectionManager;
        this.gcpIdTokenProvider = gcpIdTokenProvider;
        this.paymentServiceClientProperties = paymentServiceClientProperties;
    }

    public RolePolicyResponse execute(PaymentBO paymentBO) {
        log.debug("Subscription Plans URL invoked : {}", paymentServiceApiProperties.getRolePolicyResource());

        Map<String, Object> queryParams = new HashMap<>();
        queryParams.put("country", paymentBO.getCountry());
        queryParams.put("userType", paymentBO.getUserType());
        ResponseEntity<String> response =
                webClient
                        .get()
                        .uri(uriBuilder -> {
                            uriBuilder.path(paymentServiceApiProperties.getRolePolicyResource());
                            queryParams.forEach(uriBuilder::queryParam);
                            return uriBuilder.build();
                        })
                        .header(AUTHORIZATION, "Bearer " + gcpIdTokenProvider.getIdToken(paymentServiceClientProperties.getBaseUrl()))
                        .header(X_CORRELATION_ID, paymentBO.getCorrelationId())
                        .header(X_CLIENT_APP_ID, paymentServiceApiProperties.getClientAppId())
                        .header(X_REQUEST_DATE, paymentBO.getRequestDate())
                        .exchangeToMono(clientResponse -> clientResponse.toEntity(String.class))
                        .block();

        log.debug("Subscription Plans API returned with Response Code:{}", response.getStatusCode().value());
        log.debug(poolingAsyncClientConnectionManager.getTotalStats().toString());
        return this.processResponse(response);
    }

    private RolePolicyResponse processResponse(ResponseEntity<String> response) {
        if (response.hasBody() && response.getStatusCode().value() != NOT_FOUND.value()) {
            String responseBody = response.getBody();
            if (OK.value() == response.getStatusCode().value()) {
                log.debug("Subscription Plans Response body:{}", responseBody);
                return JsonParser.deSerialize(responseBody, RolePolicyResponse.class);
            } else {
                log.error("Subscription Plans API returned with error code: {} and body: {}", response.getStatusCode().value(), response.getBody());
                throw new RequestProcessingException("Unable to fetch Subscription Plans data.");
            }
        } else {
            if (NOT_FOUND.value() == response.getStatusCode().value()) {
                log.error("Response Status:{}", response.getStatusCode().value());
                throw new NoDataFoundException("Subscription Plans Not Found.");
            }
            log.error("Subscription Plans API returned without body");
            throw new RequestProcessingException("Unable to fetch Subscription Plans data.");
        }
    }
}
