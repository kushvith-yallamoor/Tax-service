package com.blockstats.tax.api;

import static com.blockstats.commons.constants.HeaderConstants.*;
import static com.blockstats.commons.constants.MDCConstants.*;
import static com.blockstats.tax.constant.Constants.TAX_SERVICE;

import java.net.http.HttpHeaders;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.blockstats.common.tax.bo.TaxBO;
import com.blockstats.common.tax.delegate.mongo.entity.UserEntity;
import com.blockstats.common.tax.delegate.mongo.repository.UserRepository;
import com.blockstats.common.tax.dto.TaxConfigurationDto;
import com.blockstats.common.tax.dto.TaxFinancialYearDto;
import com.blockstats.common.tax.factory.TaxServiceFactory;
import com.blockstats.common.tax.service.TaxConfigurationService;
import com.blockstats.commons.api.authorization.models.AuthorizeUser;
import com.blockstats.commons.constants.StatsConstants;
import com.blockstats.commons.stats.StatsCollector;
import com.blockstats.enums.TrackingStatus;
import com.blockstats.response.TaxResponse;
import com.blockstats.response.tracking.TrackingResponseDTO;
import com.blockstats.tax.api.validator.RequestValidator;
import com.blockstats.tax.delegate.CreateInitialStatusRecordDelegate;
import com.blockstats.tax.delegate.PubSubPublishDelegator;
import com.blockstats.tax.service.TaxDocumentService;
import com.blockstats.tax.service.TaxSummaryService;

import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * This class expose an API endpoints for tax summary
 */
@Slf4j
@RestController
@RequestMapping("/v1/tax")
@AllArgsConstructor
public class TaxController {

	private final RequestValidator requestValidator;
	private final TaxServiceFactory taxServiceFactory;
	private final TaxSummaryService taxSummaryService;
	private final TaxDocumentService taxDocumentService;
	private final TaxConfigurationService taxConfigurationService;
	private final CreateInitialStatusRecordDelegate createInitialStatusRecordDelegate;
    private final PubSubPublishDelegator pubSubPublishDelegator;
	private final UserRepository userRepository;

    /**
     *
     * @param userId
     * @param country
     * @param financialYear
     * @return TaxResponse
     * @throws ExecutionException
     */
    @GetMapping("/{userId}")
    @AuthorizeUser
    public ResponseEntity<TaxResponse> getTaxSummary(@PathVariable("userId") String userId,
                                                     @RequestParam(name = "country") String country,
                                                     @RequestParam(name = "financialYear") String financialYear,
                                                     @RequestHeader HttpHeaders httpHeaders) {
        this.setMDCInfo(httpHeaders, userId, country);
        StatsCollector.putInCurrentSession(StatsConstants.EVENT, "GET_TAX_SUMMARY");
        StatsCollector.putInCurrentSession(StatsConstants.FINANCIAL_YEAR, financialYear);
        requestValidator.validateFinancialYear(country, financialYear);
        return ResponseEntity.ok(taxSummaryService.getTaxSummary(buildTaxBO(httpHeaders, userId, country, financialYear)));
    }

    /**
     *
     * @param userId
     * @param country
     * @param financialYear
     * @return String
     * @throws ExecutionException
     */
    @GetMapping("/refresh/{userId}")
    @AuthorizeUser
    public ResponseEntity<String> refreshTaxSummary(@PathVariable("userId") String userId,
                                                    @RequestParam(name = "country") String country,
                                                    @RequestParam(name = "financialYear") String financialYear,
                                                    @RequestHeader HttpHeaders httpHeaders) {
        this.setMDCInfo(httpHeaders, userId, country);
        StatsCollector.putInCurrentSession(StatsConstants.EVENT, "REFRESH_TAX_SUMMARY");
        StatsCollector.putInCurrentSession(StatsConstants.FINANCIAL_YEAR, financialYear);
        requestValidator.validateFinancialYear(country, financialYear);
        TaxBO taxBO = this.buildTaxBO(httpHeaders, userId, country, financialYear);

        TrackingResponseDTO trackingResponseDTO = this.createInitialStatusRecordDelegate.execute(taxBO);

        if (Objects.isNull(trackingResponseDTO) || CollectionUtils.isEmpty(trackingResponseDTO.getWalletStatuses())) {
            log.debug("Tax Generation is already in progress");
            return ResponseEntity.status(201).body("Previous request for Tax summary generation is in progress");
        }
        try {
            this.pubSubPublishDelegator.execute(taxBO, TrackingStatus.IN_PROGRESS);
            taxServiceFactory.getTaxService(getTaxBeanName(country)).refreshTaxSummary(taxBO);
        } catch (Throwable throwable) {
            log.error("Tax Generation failed", throwable);
            this.pubSubPublishDelegator.execute(taxBO, TrackingStatus.COMPLETED_WITH_ERRORS);
            throw throwable;
        }
        this.pubSubPublishDelegator.execute(taxBO, TrackingStatus.COMPLETED_SUCCESSFULLY);
        return ResponseEntity.status(201).body("Tax summary generation is in progress");
    }

    /**
     *
     * @param userId
     * @param country
     * @param financialYear
     * @param response
     * @return
     * @throws ExecutionException
     */
    @GetMapping("/document/{userId}")
    @AuthorizeUser
    public void downloadTaxDocument(@PathVariable("userId") String userId,
                                    @RequestParam(name = "country") String country,
                                    @RequestParam(name = "financialYear") String financialYear,
                                    @RequestParam(name = "documentName") String documentName,
                                    @RequestHeader HttpHeaders httpHeaders,
                                    HttpServletResponse response) {
        this.setMDCInfo(httpHeaders, userId, country);
        StatsCollector.putInCurrentSession(StatsConstants.EVENT, "DOWNLOAD_TAX_DOCUMENT");
        StatsCollector.putInCurrentSession(StatsConstants.FINANCIAL_YEAR, financialYear);
        StatsCollector.putInCurrentSession(StatsConstants.FILE_NAME, documentName);
        requestValidator.validateFinancialYearAndDocumentName(country, financialYear, documentName);

        TaxBO taxBO = buildTaxBO(httpHeaders, userId, country, financialYear);
        taxBO.setDocumentName(documentName);

        taxDocumentService.downloadDocument(taxBO, response);
    }

    @GetMapping("/financial-years")
    public ResponseEntity<List<TaxFinancialYearDto>> getTaxFinancialYears(@RequestParam(name = "country") String country) {
        requestValidator.validateCountry(country);
        TaxConfigurationDto taxConfiguration = taxConfigurationService.getTaxConfiguration(country);

        List<TaxFinancialYearDto> taxFinancialYears = taxConfiguration.getFinancialYears().values()
                .stream()
                .map(financialYear -> TaxFinancialYearDto.builder()
                        .name(financialYear.getName())
                        .displayName(financialYear.getDisplayName())
                        .enabled(financialYear.isEnabled())
                        .startDate(financialYear.getStartDate())
                        .endDate(financialYear.getEndDate())
                        .build())
                .toList();

        return ResponseEntity.ok(taxFinancialYears);
    }

    private String getTaxBeanName(String country) {
        return TAX_SERVICE.concat(country);
    }

    private TaxBO buildTaxBO(HttpHeaders headers, String userId, String country, String financialYear) {
        TaxFinancialYearDto taxFinancialYear = taxConfigurationService.getTaxFinancialYear(country, financialYear);
        UserEntity user = userRepository.findByUserId(userId).block();
        return TaxBO.builder()
                .correlationId(headers.getFirst(X_CORRELATION_ID))
                .clientAppId(headers.getFirst(X_CLIENT_APP_ID))
                .requestDate(headers.getFirst(X_REQUEST_DATE))
                .userId(userId)
                .country(country)
                .financialYear(taxFinancialYear)
                .currency(user.getPreferredCurrency())
				.costBasisType(user.getCostBasisType())
                .build();
    }

    private void setMDCInfo(HttpHeaders headers, String userId, String country) {
        MDC.put(MDC_CORRELATION_ID, headers.getFirst(X_CORRELATION_ID));
        MDC.put(MDC_REQUEST_ID, headers.getFirst(X_REQUEST_ID));
        MDC.put(MDC_CREATED_TIME, headers.getFirst(X_REQUEST_DATE));
        MDC.put(CLIENT_APP_ID, headers.getFirst(X_CLIENT_APP_ID));
        MDC.put(MDC_USER_ID, String.format(MDC_USER_ID_FORMAT, userId));
        MDC.put(MDC_SOURCE, "TAX");
        MDC.put(MDC_SOURCE_TYPE, StringUtils.toRootUpperCase(country));
    }

}
