package com.blockstats.tax.delegate;

import com.blockstats.commons.util.JsonParser;
import com.blockstats.tax.dto.AiLabelingResponse;
import com.blockstats.tax.gcp.auth.GCPIdTokenProvider;
import com.blockstats.tax.properties.AiServiceApiProperties;
import com.blockstats.tax.properties.AiServiceClientProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Repository;
import org.springframework.web.reactive.function.client.WebClient;

import static com.blockstats.tax.constant.Constants.AI_CLIENT_CONN_POOL;
import static com.blockstats.tax.constant.Constants.AI_WEB_CLIENT;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpStatus.OK;

@Repository
@Slf4j
public class AiLabelingDelegate {

    private final AiServiceApiProperties aiServiceApiProperties;
    private final AiServiceClientProperties aiServiceClientProperties;
    private final WebClient webClient;
    private final PoolingAsyncClientConnectionManager poolingAsyncClientConnectionManager;
    private final GCPIdTokenProvider gcpIdTokenProvider;

    public AiLabelingDelegate(AiServiceApiProperties aiServiceApiProperties,
                               AiServiceClientProperties aiServiceClientProperties,
                               @Qualifier(AI_WEB_CLIENT) WebClient webClient,
                               @Qualifier(AI_CLIENT_CONN_POOL) PoolingAsyncClientConnectionManager poolingAsyncClientConnectionManager,
                               GCPIdTokenProvider gcpIdTokenProvider) {
        this.aiServiceApiProperties = aiServiceApiProperties;
        this.aiServiceClientProperties = aiServiceClientProperties;
        this.webClient = webClient;
        this.poolingAsyncClientConnectionManager = poolingAsyncClientConnectionManager;
        this.gcpIdTokenProvider = gcpIdTokenProvider;
    }

    public AiLabelingResponse getLabel(String chainName, String txHash) {
        String uri = aiServiceApiProperties.getLabelResource()
                .replace("{chainName}", chainName)
                .replace("{txHash}", txHash);

        log.debug("AI Labeling URL invoked : {}", uri);

        ResponseEntity<String> responseEntity =
                webClient
                        .get()
                        .uri(uri)
                        .header(AUTHORIZATION, "Bearer " + gcpIdTokenProvider.getIdToken(aiServiceClientProperties.getBaseUrl()))
                        .exchangeToMono(clientResponse -> clientResponse.toEntity(String.class))
                        .block();

        log.debug("AI Labeling API returned with Response Code:{}", responseEntity.getStatusCode().value());
        log.debug(poolingAsyncClientConnectionManager.getTotalStats().toString());
        return this.processResponse(responseEntity);
    }

    private AiLabelingResponse processResponse(ResponseEntity<String> response) {
        if (response.hasBody() && OK.value() == response.getStatusCode().value()) {
            return JsonParser.deSerialize(response.getBody(), AiLabelingResponse.class);
        }
        log.warn("AI Labeling API returned with code:{} and body:{}", response.getStatusCode().value(), response.getBody());
        return null;
    }
}
