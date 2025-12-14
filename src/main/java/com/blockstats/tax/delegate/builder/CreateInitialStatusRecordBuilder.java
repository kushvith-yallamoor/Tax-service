package com.blockstats.tax.delegate.builder;

import com.blockstats.common.tax.bo.TaxBO;
import com.blockstats.enums.PortfolioRequestType;
import com.blockstats.request.tracking.TrackingRequestDTO;
import org.springframework.stereotype.Service;

@Service
public class CreateInitialStatusRecordBuilder {

    public TrackingRequestDTO build(TaxBO taxBO) {
        TrackingRequestDTO trackingRequestDTO = new TrackingRequestDTO();
        trackingRequestDTO.setUserId(taxBO.getUserId());
        trackingRequestDTO.setPortfolioRequestType(PortfolioRequestType.GENERATE_TAX_SUMMARY);
        return trackingRequestDTO;
    }
}
