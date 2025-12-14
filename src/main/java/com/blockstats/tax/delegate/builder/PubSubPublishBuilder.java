package com.blockstats.tax.delegate.builder;

import com.blockstats.common.tax.bo.TaxBO;
import com.blockstats.enums.PortfolioRequestType;
import com.blockstats.enums.TrackingStatus;
import com.blockstats.queue.TrackingRequestDTO;
import org.springframework.stereotype.Service;

@Service
public class PubSubPublishBuilder {

    public TrackingRequestDTO build(TaxBO taxBO, TrackingStatus trackingStatus) {
        TrackingRequestDTO trackingRequestDTO = new TrackingRequestDTO();
        trackingRequestDTO.setVersion("1.0");
        trackingRequestDTO.setUserId(taxBO.getUserId());
        trackingRequestDTO.setCorrelationId(taxBO.getCorrelationId());
        trackingRequestDTO.setPortfolioRequestType(PortfolioRequestType.GENERATE_TAX_SUMMARY);
        trackingRequestDTO.setStatus(trackingStatus);
        trackingRequestDTO.setRequestedBy("Tax_Service");
        return trackingRequestDTO;
    }
}
