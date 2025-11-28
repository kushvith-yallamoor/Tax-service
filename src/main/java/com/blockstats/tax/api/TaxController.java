package com.blockstats.tax.api;

import static com.blockstats.tax.constant.Constants.TAX_SERVICE;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.blockstats.common.mongo.userdata.delegate.entity.TaxSummary;
import com.blockstats.common.mongo.userdata.delegate.repository.TaxSummaryRepository;
import com.blockstats.common.tax.bo.TaxBO;
import com.blockstats.common.tax.delegate.mongo.entity.TaxFinancialYearConfig;
import com.blockstats.common.tax.factory.TaxServiceFactory;
import com.blockstats.commons.api.authorization.models.AuthorizeUser;
import com.blockstats.response.TaxResponse;
import com.blockstats.tax.api.validator.RequestValidator;
import com.blockstats.tax.mapper.TaxMapper;

import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;

/**
 * This class expose an API endpoints for tax summary
 */
@RestController
@RequestMapping("/v1/tax")
@AllArgsConstructor
public class TaxController {

	private final TaxServiceFactory taxServiceFactory;
	private final TaxSummaryRepository taxSummaryRepository;
	private final RequestValidator requestValidator;
	private final TaxMapper taxMapper;

	/**
	 * 
	 * @param userId
	 * @param country
	 * @param financialYear
	 * @return TaxResponse
	 */
	@GetMapping("/{userId}")
	@AuthorizeUser
	public ResponseEntity<TaxResponse> getTaxSummary(@PathVariable("userId") String userId,
			@RequestParam(name = "country") String country,
			@RequestParam(name = "financialYear") String financialYear) {
		requestValidator.validateFinancialYear(country, financialYear);
		TaxSummary taxSummary = taxSummaryRepository.findByUserIdAndCountryAndFinancialYear(userId, country, financialYear).block();
		TaxResponse taxResponse = taxMapper.mapToTaxResponse(taxSummary);
		return ResponseEntity.ok(taxResponse);
	}

	/**
	 * 
	 * @param userId
	 * @param country
	 * @param financialYear
	 * @return String
	 */
	@GetMapping("/refersh/{userId}")
	@AuthorizeUser
	public ResponseEntity<String> refreshTaxSummary(@PathVariable("userId") String userId,
			@RequestParam(name = "country") String country,
			@RequestParam(name = "financialYear") String financialYear) {
		TaxFinancialYearConfig financialYearConfig = requestValidator.validateFinancialYear(country, financialYear);
		taxServiceFactory.getTaxService(getTaxBeanName(country)).refreshTaxSummary(buildTaxBO(userId, country, financialYearConfig));
		return ResponseEntity.status(201).body("Tax summary generation is in progress");
	}
	
	/**
	 * 
	 * @param userId
	 * @param country
	 * @param financialYear
	 * @param response
	 * @return
	 */
	@GetMapping("/report/{userId}")
	@AuthorizeUser
	public ResponseEntity<?> downloadReport(@PathVariable("userId") String userId,
			@RequestParam(name = "country") String country,
			@RequestParam(name = "financialYear") String financialYear,
			HttpServletResponse response) {
		requestValidator.validateFinancialYear(country, financialYear);
		
		HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Disposition", "attachment; filename=download.pdf");
        headers.add("Content-Type", "application/pdf");
    	return new ResponseEntity<>(null, headers, HttpStatus.OK);
	}

	private String getTaxBeanName(String country) {
		return TAX_SERVICE.concat(country);
	}
	
	private TaxBO buildTaxBO(String userId, String country, TaxFinancialYearConfig financialYearConfig) {
		return TaxBO.builder()
				.userId(userId)
				.country(country)
				.financialYear(financialYearConfig.getName())
				.startDate(financialYearConfig.getStartDate())
				.endDate(financialYearConfig.getEndDate())
				.build();
	}

}
