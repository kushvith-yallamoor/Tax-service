package com.blockstats.tax.delegate;

import static com.blockstats.tax.constant.Constants.AI_CLIENT_CONN_POOL;
import static com.blockstats.tax.constant.Constants.AI_WEB_CLIENT;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpStatus.OK;

import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Repository;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import com.blockstats.commons.util.JsonParser;
import com.blockstats.tax.dto.Form1099DaAiParseResponse;
import com.blockstats.tax.gcp.auth.GCPIdTokenProvider;
import com.blockstats.tax.properties.AiServiceApiProperties;
import com.blockstats.tax.properties.AiServiceClientProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * Sends a user-uploaded broker 1099-DA PDF to ai-service for AI-based extraction.
 * The response is untrusted, broker-reported data - callers must reconcile it against
 * the user's own transaction history (see Form1099DaReconciliationService), never write
 * it directly into transactions or tax forms.
 */
@Repository
@Slf4j
public class Form1099DaParsingDelegate {

	private final AiServiceApiProperties aiServiceApiProperties;
	private final AiServiceClientProperties aiServiceClientProperties;
	private final WebClient webClient;
	private final PoolingAsyncClientConnectionManager poolingAsyncClientConnectionManager;
	private final GCPIdTokenProvider gcpIdTokenProvider;

	public Form1099DaParsingDelegate(AiServiceApiProperties aiServiceApiProperties,
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

	public Form1099DaAiParseResponse parse(byte[] pdfBytes, String filename) {
		String uri = aiServiceApiProperties.getForm1099DaParseResource();

		log.debug("1099-DA parse URL invoked : {}", uri);

		MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
		bodyBuilder.part("file", new ByteArrayResource(pdfBytes) {
			@Override
			public String getFilename() {
				return filename;
			}
		}).contentType(MediaType.APPLICATION_PDF);

		ResponseEntity<String> responseEntity =
				webClient
						.post()
						.uri(uri)
						.header(AUTHORIZATION, "Bearer " + gcpIdTokenProvider.getIdToken(aiServiceClientProperties.getBaseUrl()))
						.contentType(MediaType.MULTIPART_FORM_DATA)
						.body(BodyInserters.fromMultipartData(bodyBuilder.build()))
						.exchangeToMono(clientResponse -> clientResponse.toEntity(String.class))
						.block();

		log.debug("1099-DA parse API returned with Response Code:{}", responseEntity.getStatusCode().value());
		log.debug(poolingAsyncClientConnectionManager.getTotalStats().toString());
		return this.processResponse(responseEntity);
	}

	private Form1099DaAiParseResponse processResponse(ResponseEntity<String> response) {
		if (response.hasBody() && OK.value() == response.getStatusCode().value()) {
			return JsonParser.deSerialize(response.getBody(), Form1099DaAiParseResponse.class);
		}
		log.warn("1099-DA parse API returned with code:{} and body:{}", response.getStatusCode().value(), response.getBody());
		return null;
	}

}
