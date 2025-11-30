package com.blockstats.tax.api;

import static com.blockstats.tax.constant.Constants.TAX_SERVICE;

import java.util.List;
import java.util.concurrent.ExecutionException;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.blockstats.common.tax.bo.TaxBO;
import com.blockstats.common.tax.dto.TaxConfigurationDto;
import com.blockstats.common.tax.dto.TaxFinancialYearDto;
import com.blockstats.common.tax.factory.TaxServiceFactory;
import com.blockstats.common.tax.service.TaxConfigurationService;
import com.blockstats.commons.api.authorization.models.AuthorizeUser;
import com.blockstats.response.TaxResponse;
import com.blockstats.tax.api.validator.RequestValidator;
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
			@RequestParam(name = "financialYear") String financialYear) {
		requestValidator.validateFinancialYear(country, financialYear);
		return ResponseEntity.ok(taxSummaryService.getTaxSummary(buildTaxBO(userId, country, financialYear)));
	}

	/**
	 * 
	 * @param userId
	 * @param country
	 * @param financialYear
	 * @return String
	 * @throws ExecutionException 
	 */
	@GetMapping("/refersh/{userId}")
	@AuthorizeUser
	public ResponseEntity<String> refreshTaxSummary(@PathVariable("userId") String userId,
			@RequestParam(name = "country") String country,
			@RequestParam(name = "financialYear") String financialYear) {
		requestValidator.validateFinancialYear(country, financialYear);
		taxServiceFactory.getTaxService(getTaxBeanName(country)).refreshTaxSummary(buildTaxBO(userId, country, financialYear));
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
			HttpServletResponse response) {
		requestValidator.validateFinancialYearAndDocumentName(country, financialYear, documentName);
		
		TaxBO taxBO = buildTaxBO(userId, country, financialYear);
		taxBO.setDocumentName(documentName);
		
        taxDocumentService.downloadDocument(taxBO, response);
	}
	
	@GetMapping("/financial-years/{userId}")
	@AuthorizeUser
	public ResponseEntity<List<TaxFinancialYearDto>> getTaxFinancialYears(@PathVariable("userId") String userId,
			@RequestParam(name = "country") String country) {
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
	
	private TaxBO buildTaxBO(String userId, String country, String financialYear) {
		TaxFinancialYearDto taxFinancialYear = taxConfigurationService.getTaxFinancialYear(country, financialYear);
		return TaxBO.builder()
				.userId(userId)
				.country(country)
				.financialYear(taxFinancialYear)
				.build();
	}

}
