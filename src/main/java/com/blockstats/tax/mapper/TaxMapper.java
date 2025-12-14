package com.blockstats.tax.mapper;

import com.blockstats.common.mongo.userdata.delegate.entity.TaxSummary;
import com.blockstats.response.TaxResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface TaxMapper {

    TaxResponse mapToTaxResponse(TaxSummary taxsummary);
}
