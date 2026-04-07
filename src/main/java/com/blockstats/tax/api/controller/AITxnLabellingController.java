package com.blockstats.tax.api.controller;

import com.blockstats.commons.api.authorization.bean.UserContext;
import com.blockstats.commons.constants.StatsConstants;
import com.blockstats.commons.stats.StatsCollector;
import com.blockstats.tax.bo.AITxnLabellingBO;
import com.blockstats.tax.service.AITxnLabellingService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.blockstats.commons.constants.HeaderConstants.*;
import static com.blockstats.commons.constants.MDCConstants.*;

@Slf4j
@RestController
@RequestMapping("/v1/aiTxnLabelling")
@AllArgsConstructor
public class AITxnLabellingController {

    private final AITxnLabellingService aiTxnLabellingService;

    @GetMapping
    public ResponseEntity<String> aiTxnLabelling(@RequestHeader HttpHeaders httpHeaders,
                                                 @AuthenticationPrincipal UserContext userContext) {
        this.setMDCInfo(httpHeaders, userContext.getUserId());
        StatsCollector.putInCurrentSession(StatsConstants.EVENT, "AI_TXN_LABELLING");
        AITxnLabellingBO aiTxnLabellingBO = this.buildAITxnLabellingBO(httpHeaders, userContext.getUserId());
        aiTxnLabellingService.invoke(aiTxnLabellingBO);
        return ResponseEntity.status(HttpStatus.CREATED).body("Initiated AI Labelling for Txns");
    }

    private void setMDCInfo(HttpHeaders headers, String userId) {
        MDC.put(MDC_CORRELATION_ID, headers.getFirst(X_CORRELATION_ID));
        MDC.put(MDC_REQUEST_ID, headers.getFirst(X_REQUEST_ID));
        MDC.put(MDC_CREATED_TIME, headers.getFirst(X_REQUEST_DATE));
        MDC.put(CLIENT_APP_ID, headers.getFirst(X_CLIENT_APP_ID));
        MDC.put(MDC_USER_ID, String.format(MDC_USER_ID_FORMAT, userId));
        MDC.put(MDC_SOURCE, "AI_TXN_LABELLING");
    }

    private AITxnLabellingBO buildAITxnLabellingBO(HttpHeaders headers, String userId) {
        return AITxnLabellingBO
                .builder()
                .correlationId(headers.getFirst(X_CORRELATION_ID))
                .clientAppId(headers.getFirst(X_CLIENT_APP_ID))
                .requestDate(headers.getFirst(X_REQUEST_DATE))
                .userId(userId)
                .build();
    }
}
