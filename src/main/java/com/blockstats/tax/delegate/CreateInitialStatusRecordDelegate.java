package com.blockstats.tax.delegate;

import com.blockstats.common.tax.bo.TaxBO;
import com.blockstats.commons.exception.RequestProcessingException;
import com.blockstats.commons.util.JsonParser;
import com.blockstats.request.tracking.TrackingRequestDTO;
import com.blockstats.response.tracking.TrackingResponseDTO;
import com.blockstats.tax.delegate.builder.CreateInitialStatusRecordBuilder;
import com.blockstats.tax.gcp.auth.GCPIdTokenProvider;
import com.blockstats.tax.properties.TrackingServiceApiProperties;
import com.blockstats.tax.properties.TrackingServiceClientProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Repository;
import org.springframework.web.reactive.function.client.WebClient;

import static com.blockstats.commons.constants.HeaderConstants.*;
import static com.blockstats.tax.constant.Constants.TRACKING_CLIENT_CONN_POOL;
import static com.blockstats.tax.constant.Constants.TRACKING_WEB_CLIENT;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.HttpStatus.OK;

@Repository
@Slf4j
public class CreateInitialStatusRecordDelegate {

    private final TrackingServiceApiProperties trackingServiceApiProperties;
    private final CreateInitialStatusRecordBuilder createInitialStatusRecordBuilder;
    private final WebClient webClient;
    private final PoolingAsyncClientConnectionManager poolingAsyncClientConnectionManager;
    private final GCPIdTokenProvider gcpIdTokenProvider;
    private final TrackingServiceClientProperties trackingServiceClientProperties;

    public CreateInitialStatusRecordDelegate(TrackingServiceApiProperties trackingServiceApiProperties,
                                             CreateInitialStatusRecordBuilder createInitialStatusRecordBuilder,
                                             @Qualifier(TRACKING_WEB_CLIENT) WebClient webClient,
                                             @Qualifier(TRACKING_CLIENT_CONN_POOL) PoolingAsyncClientConnectionManager poolingAsyncClientConnectionManager,
                                             GCPIdTokenProvider gcpIdTokenProvider,
                                             TrackingServiceClientProperties trackingServiceClientProperties) {
        this.trackingServiceApiProperties = trackingServiceApiProperties;
        this.createInitialStatusRecordBuilder = createInitialStatusRecordBuilder;
        this.webClient = webClient;
        this.poolingAsyncClientConnectionManager = poolingAsyncClientConnectionManager;
        this.gcpIdTokenProvider = gcpIdTokenProvider;
        this.trackingServiceClientProperties = trackingServiceClientProperties;
    }

    public TrackingResponseDTO execute(TaxBO taxBO) {
        TrackingRequestDTO trackingRequestDTO = this.createInitialStatusRecordBuilder.build(taxBO);
        log.debug("CreateInitialStatusRecord URL invoked : {}", trackingServiceApiProperties.getTrackingServiceResource());

        ResponseEntity<String> responseEntity =
                webClient
                        .post()
                        .uri(trackingServiceApiProperties.getTrackingServiceResource())
                        .header(AUTHORIZATION, "Bearer " + gcpIdTokenProvider.getIdToken(trackingServiceClientProperties.getBaseUrl()))
                        .header(X_CORRELATION_ID, taxBO.getCorrelationId())
                        .header(X_CLIENT_APP_ID, trackingServiceApiProperties.getClientAppId())
                        .header(X_REQUEST_DATE, taxBO.getRequestDate())
                        .header(CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .bodyValue(trackingRequestDTO)
                        .exchangeToMono(clientResponse -> clientResponse.toEntity(String.class))
                        .block();

        log.debug("API returned with Response Code:{}", responseEntity.getStatusCode().value());
        log.debug(poolingAsyncClientConnectionManager.getTotalStats().toString());
        return this.processResponse(responseEntity);
    }

    private TrackingResponseDTO processResponse(ResponseEntity<String> response) {
        if (response.hasBody()) {
            String responseBody = response.getBody();
            if (OK.value() == response.getStatusCode().value()) {
                log.debug("{} Response body:{}", responseBody);
                return JsonParser.deSerialize(responseBody, TrackingResponseDTO.class);
            } else {
                log.error("{} API returned with error code: {} and body: {}", response.getStatusCode().value(), response.getBody());
                throw new RequestProcessingException("Unable to Create Initial Record Data.");
            }
        } else {
            log.error("API returned without body");
            throw new RequestProcessingException("Unable to Create Initial Data.");
        }
    }
}
