package com.blockstats.tax.delegate;

import com.blockstats.common.tax.bo.TaxBO;
import com.blockstats.commons.util.JsonParser;
import com.blockstats.enums.TrackingStatus;
import com.blockstats.queue.TrackingRequestDTO;
import com.blockstats.tax.delegate.builder.PubSubPublishBuilder;
import com.blockstats.tax.properties.AppProperties;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.concurrent.ExecutionException;

@Repository
@AllArgsConstructor
@Slf4j
public class PubSubPublishDelegator {

    private final PubSubTemplate pubSubTemplate;
    private final AppProperties appProperties;
    private final PubSubPublishBuilder pubSubPublishBuilder;

    public void execute(TaxBO taxBO, TrackingStatus trackingStatus) {
        TrackingRequestDTO trackingRequestDTO = this.pubSubPublishBuilder.build(taxBO, trackingStatus);
        this.publish(trackingRequestDTO);
    }

    private void publish(TrackingRequestDTO trackingRequestDTO) {
        try {
            PubsubMessage pubsubMessage = PubsubMessage
                    .newBuilder()
                    .setOrderingKey(trackingRequestDTO.getCorrelationId())
                    .setData(ByteString.copyFromUtf8(JsonParser.serialize(trackingRequestDTO)))
                    .build();

            this.pubSubTemplate
                    .publish(this.appProperties.getTrackingServiceTopic(), pubsubMessage)
                    .get();
            log.debug("Reported Tracking Status as {}", trackingRequestDTO.getStatus());
        } catch (InterruptedException e) {
            log.error("Unable to publish walletDetails of user {}", trackingRequestDTO.getUserId(), e);
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            log.error("Unable to publish walletDetails of user {}", trackingRequestDTO.getUserId(), e);
            throw new RuntimeException(e);
        }
    }

}
