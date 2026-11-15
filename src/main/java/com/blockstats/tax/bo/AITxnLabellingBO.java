package com.blockstats.tax.bo;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class AITxnLabellingBO {
    private String correlationId;
    private String clientAppId;
    private String requestDate;
    private String userId;
    private long userType;
    private java.util.Set<String> roles;
}
