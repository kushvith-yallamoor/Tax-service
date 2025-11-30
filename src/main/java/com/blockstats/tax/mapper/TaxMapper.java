package com.blockstats.tax.mapper;

import org.mapstruct.Mapper;

import com.blockstats.common.mongo.userdata.delegate.entity.TaxSummary;
import com.blockstats.response.TaxResponse;

@Mapper(componentModel = "spring")
public interface TaxMapper {

	TaxResponse mapToTaxResponse(TaxSummary taxsummary);
}
