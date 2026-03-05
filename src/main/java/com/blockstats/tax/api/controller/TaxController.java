package com.blockstats.tax.api.controller;

import com.blockstats.common.tax.bo.TaxBO;
import com.blockstats.common.tax.dto.TaxConfigurationDto;
import com.blockstats.common.tax.dto.TaxFinancialYearDto;
import com.blockstats.common.tax.factory.TaxServiceFactory;
import com.blockstats.common.tax.service.TaxConfigurationService;
import com.blockstats.commons.api.authorization.bean.UserContext;
import com.blockstats.commons.constants.StatsConstants;
import com.blockstats.commons.exception.InsufficientPlanException;
import com.blockstats.commons.exception.InvalidRequestException;
import com.blockstats.commons.exception.RequestProcessingException;
import com.blockstats.commons.exception.Violation;
import com.blockstats.commons.stats.StatsCollector;
import com.blockstats.enums.CostBasisType;
import com.blockstats.enums.TrackingStatus;
import com.blockstats.response.RecommendedTaxPlanResponse;
import com.blockstats.response.TaxResponse;
import com.blockstats.response.UserPreferencesResponse;
import com.blockstats.response.tracking.TrackingResponseDTO;
import com.blockstats.tax.api.validator.RequestValidator;
import com.blockstats.tax.delegate.CreateInitialStatusRecordDelegate;
import com.blockstats.tax.delegate.FetchUserPreferenceDelegate;
import com.blockstats.tax.delegate.PubSubPublishDelegator;
import com.blockstats.tax.service.TaxDocumentService;
import com.blockstats.tax.service.TaxSummaryService;
import com.blockstats.tax.service.UserSubscriptionValidationService;

import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

import static com.blockstats.commons.constants.HeaderConstants.*;
import static com.blockstats.commons.constants.MDCConstants.*;
import static com.blockstats.tax.constant.Constants.BASIC_TAX_SERVICE;
import static com.blockstats.tax.constant.Constants.SUBSCRIPTION_TAX_SERVICE;
import static com.blockstats.tax.constant.Constants.TAX_REFRESH_THREAD_POOL;

/**
 * This class expose an API endpoints for tax summary
 */
@Slf4j
@RestController
@RequestMapping("/v1/tax")
public class TaxController {

    private final RequestValidator requestValidator;
    private final TaxServiceFactory taxServiceFactory;
    private final TaxSummaryService taxSummaryService;
    private final TaxDocumentService taxDocumentService;
    private final TaxConfigurationService taxConfigurationService;
    private final CreateInitialStatusRecordDelegate createInitialStatusRecordDelegate;
    private final PubSubPublishDelegator pubSubPublishDelegator;
    private final FetchUserPreferenceDelegate fetchUserPreferenceDelegate;
    private final UserSubscriptionValidationService userSubscriptionValidationService;
    private final Executor executor;
    
    public TaxController(RequestValidator requestValidator, TaxServiceFactory taxServiceFactory,
			TaxSummaryService taxSummaryService, TaxDocumentService taxDocumentService,
			TaxConfigurationService taxConfigurationService,
			CreateInitialStatusRecordDelegate createInitialStatusRecordDelegate,
			PubSubPublishDelegator pubSubPublishDelegator, FetchUserPreferenceDelegate fetchUserPreferenceDelegate,
			UserSubscriptionValidationService userSubscriptionValidationService, 
			@Qualifier(TAX_REFRESH_THREAD_POOL) Executor executor) {
		this.requestValidator = requestValidator;
		this.taxServiceFactory = taxServiceFactory;
		this.taxSummaryService = taxSummaryService;
		this.taxDocumentService = taxDocumentService;
		this.taxConfigurationService = taxConfigurationService;
		this.createInitialStatusRecordDelegate = createInitialStatusRecordDelegate;
		this.pubSubPublishDelegator = pubSubPublishDelegator;
		this.fetchUserPreferenceDelegate = fetchUserPreferenceDelegate;
		this.userSubscriptionValidationService = userSubscriptionValidationService;
		this.executor = executor;
	}

    /**
     *
     * @param country
     * @param financialYear
     * @return TaxResponse
     * @throws ExecutionException
     */
    @GetMapping
    public ResponseEntity<TaxResponse> getTaxSummary(@RequestParam(name = "country") String country,
                                                     @RequestParam(name = "financialYear") String financialYear,
                                                     @RequestHeader HttpHeaders httpHeaders,
                                                     @AuthenticationPrincipal UserContext userContext) {
        this.setMDCInfo(httpHeaders, userContext.getUserId(), country);
        StatsCollector.putInCurrentSession(StatsConstants.EVENT, "GET_TAX_SUMMARY");
        StatsCollector.putInCurrentSession(StatsConstants.FINANCIAL_YEAR, financialYear);
        requestValidator.validateFinancialYear(country, financialYear);
        TaxBO taxBO = this.buildTaxBO(httpHeaders, userContext.getUserId(), country, financialYear);
        
        TaxResponse taxResponse = taxSummaryService.getTaxSummary(buildTaxBO(httpHeaders, userContext.getUserId(), country, financialYear));
        try {
        	userSubscriptionValidationService.validate(userContext, taxBO);
        } catch (InsufficientPlanException e) {
        	RecommendedTaxPlanResponse recommendedPlan = new RecommendedTaxPlanResponse();
        	recommendedPlan.setName(e.getRecommendedPlanResponse().getName());
        	recommendedPlan.setCountry(e.getRecommendedPlanResponse().getCountry());
        	recommendedPlan.setPlanId(e.getRecommendedPlanResponse().getPlanId());
        	recommendedPlan.setUserTxnCount(e.getRecommendedPlanResponse().getUserTxnCount());
        	recommendedPlan.setCostBasisType(taxResponse.getCostBasisType());
        	recommendedPlan.setCostBasisValues(taxResponse.getTaxDetails().getCostBasisValues());
        	recommendedPlan.setPnl(taxResponse.getTaxDetails().getPnl());
        	recommendedPlan.setValidSubscription(taxResponse.isValidSubscription());
        	
        	e.setRecommendedPlanResponse(recommendedPlan);
        	throw e;
		}
        return ResponseEntity.ok(taxResponse);
    }

    /**
     *
     * @param country
     * @param financialYear
     * @return String
     * @throws ExecutionException
     */
    @GetMapping("/refresh")
    public ResponseEntity<String> refreshTaxSummary(@RequestParam(name = "country") String country,
                                                    @RequestParam(name = "financialYear") String financialYear,
                                                    @RequestHeader HttpHeaders httpHeaders,
                                                    @AuthenticationPrincipal UserContext userContext) {
        this.setMDCInfo(httpHeaders, userContext.getUserId(), country);
        StatsCollector.putInCurrentSession(StatsConstants.EVENT, "REFRESH_TAX_SUMMARY");
        StatsCollector.putInCurrentSession(StatsConstants.FINANCIAL_YEAR, financialYear);
        requestValidator.validateFinancialYear(country, financialYear);
        TaxBO taxBO = this.buildTaxBO(httpHeaders, userContext.getUserId(), country, financialYear);
        
        String message = "Tax summary generation is in progress";
        // Validate user subscription, if insufficient subscription plan then calculate basic tax fields
        try {
        	userSubscriptionValidationService.validate(userContext, taxBO);
        	
        	TrackingResponseDTO trackingResponseDTO = this.createInitialStatusRecordDelegate.execute(taxBO);

            if (Objects.isNull(trackingResponseDTO) || CollectionUtils.isEmpty(trackingResponseDTO.getWalletStatuses())) {
                log.debug("Tax Generation is already in progress");
                return ResponseEntity.status(HttpStatus.CREATED).body("Previous request for Tax summary generation is in progress");
            }
            
            CompletableFuture.runAsync(() -> {
            	this.pubSubPublishDelegator.execute(taxBO, TrackingStatus.IN_PROGRESS);
                taxServiceFactory.getTaxService(SUBSCRIPTION_TAX_SERVICE.concat(country)).refreshTaxSummary(taxBO);
                this.pubSubPublishDelegator.execute(taxBO, TrackingStatus.COMPLETED_SUCCESSFULLY);
            }, executor)
            .exceptionally(ex -> {
            	log.error("Subscription Tax Generation failed:{}", ex.getMessage(), ex);
                this.pubSubPublishDelegator.execute(taxBO, TrackingStatus.COMPLETED_WITH_ERRORS);
                return null;
            });
        } catch (InsufficientPlanException e) {
        	taxServiceFactory.getTaxService(BASIC_TAX_SERVICE.concat(country)).refreshTaxSummary(taxBO);
        	message = "Tax summary generation is completed";
		}
        
        return ResponseEntity.status(HttpStatus.CREATED).body(message);
    }

    /**
     *
     * @param country
     * @param financialYear
     * @param response
     * @return
     * @throws ExecutionException
     */
    @GetMapping("/document")
    @PreAuthorize("hasRole('ROLE_TU')")
    public void downloadTaxDocument(@RequestParam(name = "country") String country,
                                    @RequestParam(name = "financialYear") String financialYear,
                                    @RequestParam(name = "documentName") String documentName,
                                    @RequestHeader HttpHeaders httpHeaders,
                                    @AuthenticationPrincipal UserContext userContext,
                                    HttpServletResponse response) {
        this.setMDCInfo(httpHeaders, userContext.getUserId(), country);
        StatsCollector.putInCurrentSession(StatsConstants.EVENT, "DOWNLOAD_TAX_DOCUMENT");
        StatsCollector.putInCurrentSession(StatsConstants.FINANCIAL_YEAR, financialYear);
        StatsCollector.putInCurrentSession(StatsConstants.FILE_NAME, documentName);
        requestValidator.validateFinancialYearAndDocumentName(country, financialYear, documentName);

        TaxBO taxBO = buildTaxBO(httpHeaders, userContext.getUserId(), country, financialYear);
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
                .sorted(Comparator.comparingInt((TaxFinancialYearDto fy) -> Integer.parseInt(fy.getName().substring(2))).reversed())
                .toList();

        return ResponseEntity.ok(taxFinancialYears);
    }

    private TaxBO buildTaxBO(HttpHeaders headers, String userId, String country, String financialYear) {
        TaxFinancialYearDto taxFinancialYear = taxConfigurationService.getTaxFinancialYear(country, financialYear);

        TaxBO taxBO = TaxBO.builder()
                .correlationId(headers.getFirst(X_CORRELATION_ID))
                .clientAppId(headers.getFirst(X_CLIENT_APP_ID))
                .requestDate(headers.getFirst(X_REQUEST_DATE))
                .userId(userId)
                .country(country)
                .financialYear(taxFinancialYear)
                .build();

        try {
            UserPreferencesResponse userPreferencesResponse = this.fetchUserPreferenceDelegate.execute(taxBO);
            if (Objects.isNull(userPreferencesResponse)) {
                throw new RequestProcessingException("Invalid User data received");
            }
            CostBasisType costBasisType = CostBasisType.fromValue(userPreferencesResponse.getCostBasisType());
            taxBO.setCostBasisType(costBasisType.value());
            taxBO.setCurrency(userPreferencesResponse.getPreferredCurrency());
        } catch (Exception e) {
            Set<Violation> violations = new HashSet<>();
            violations.add(new Violation("CostBasisType", "Please Update the CostBasisType in the User Preference."));
            throw new InvalidRequestException(violations);
        }

        return taxBO;
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
