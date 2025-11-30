package com.blockstats.tax.service;

import org.springframework.stereotype.Service;

import com.blockstats.common.mongo.userdata.delegate.entity.TaxSummary;
import com.blockstats.common.mongo.userdata.delegate.repository.TaxSummaryRepository;
import com.blockstats.common.tax.bo.TaxBO;
import com.blockstats.commons.exception.NoDataFoundException;
import com.blockstats.response.TaxResponse;
import com.blockstats.tax.mapper.TaxMapper;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class TaxSummaryService {

	private final TaxSummaryRepository taxSummaryRepository;
	private final TaxMapper taxMapper;

	public TaxResponse getTaxSummary(TaxBO taxBO) {
		TaxSummary taxSummary = taxSummaryRepository
				.findByUserIdAndCountryAndFinancialYear(taxBO.getUserId(), taxBO.getCountry(), taxBO.getFinancialYear().getName())
				.blockOptional().orElseThrow(() -> new NoDataFoundException(
						"No tax summary found for given user, country and financialYear"));
		return taxMapper.mapToTaxResponse(taxSummary);
	}

}
