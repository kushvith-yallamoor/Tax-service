package com.blockstats.tax.api.validator;

import java.util.HashSet;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.blockstats.common.tax.dto.TaxConfigurationDto;
import com.blockstats.common.tax.dto.TaxDocumentDto;
import com.blockstats.common.tax.dto.TaxFinancialYearDto;
import com.blockstats.common.tax.service.TaxConfigurationService;
import com.blockstats.commons.exception.InvalidRequestException;
import com.blockstats.commons.exception.Violation;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class RequestValidator {

	private static final String FINANCIAL_YEAR = "financialYear";
	private final TaxConfigurationService taxConfigurationService;

	public void validateFinancialYear(String country, String financialYear) {
		TaxConfigurationDto taxConfig = taxConfigurationService.getTaxConfiguration(country);
		validateTaxConfiguration(taxConfig);
		TaxFinancialYearDto financialYearDto = taxConfig.getFinancialYears().get(financialYear);
		validateFinancialYear(financialYearDto);
	}

	public void validateFinancialYearAndDocumentName(String country, String financialYear, String documentName) {
		TaxConfigurationDto taxConfig = taxConfigurationService.getTaxConfiguration(country);
		validateTaxConfiguration(taxConfig);
		TaxFinancialYearDto taxFinancialYear = taxConfig.getFinancialYears().get(financialYear);
		validateFinancialYear(taxFinancialYear);
		TaxDocumentDto taxDocument = taxFinancialYear.getDocuments().get(documentName);
		validateDocument(taxDocument);
	}

	private void validateTaxConfiguration(TaxConfigurationDto taxConfig) {
		if (taxConfig == null || !taxConfig.isEnabled()) {
			throw new InvalidRequestException(getViolation(FINANCIAL_YEAR, "System not support for given country"));
		}
	}

	private void validateFinancialYear(TaxFinancialYearDto taxFinancialYear) {
		if (taxFinancialYear == null || !taxFinancialYear.isEnabled()) {
			throw new InvalidRequestException(
					getViolation(FINANCIAL_YEAR, "System not support for given financial year"));
		}
	}

	private void validateDocument(TaxDocumentDto taxDocument) {
		if (taxDocument == null) {
			throw new InvalidRequestException(
					getViolation(FINANCIAL_YEAR, "System not support for given financial year"));
		}
	}

	private Set<Violation> getViolation(String name, String message) {
		Violation violation = new Violation(name, message);
		Set<Violation> violations = new HashSet<>();
		violations.add(violation);
		return violations;
	}

}