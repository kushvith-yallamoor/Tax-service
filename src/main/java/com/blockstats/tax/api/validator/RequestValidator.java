package com.blockstats.tax.api.validator;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.blockstats.common.tax.delegate.mongo.entity.TaxConfiguration;
import com.blockstats.common.tax.delegate.mongo.entity.TaxFinancialYearConfig;
import com.blockstats.common.tax.delegate.mongo.repository.TaxConfigurationRepository;
import com.blockstats.commons.exception.InvalidRequestException;
import com.blockstats.commons.exception.Violation;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class RequestValidator {

	private final TaxConfigurationRepository taxConfigurationRepository;

	public TaxFinancialYearConfig validateFinancialYear(String country, String financialYear) {
		TaxConfiguration taxConfig = taxConfigurationRepository.findByCountry(country).block();
		if (taxConfig == null) {
			throw new InvalidRequestException(getViolation("financialYear", "Financial year is invalid"));
		}

		Optional<TaxFinancialYearConfig> financialYearOpt = taxConfig.getFinancialYears().stream()
				.filter(year -> year.getName().equals(financialYear)).findFirst();
		if (financialYearOpt.isEmpty()) {
			throw new InvalidRequestException(getViolation("financialYear", "Financial year is invalid"));
		}
		return financialYearOpt.get();
	}

	private Set<Violation> getViolation(String name, String message) {
		Violation violation = new Violation(name, message);
		Set<Violation> violations = new HashSet<>();
		violations.add(violation);
		return violations;
	}

}