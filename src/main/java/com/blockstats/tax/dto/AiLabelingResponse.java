package com.blockstats.tax.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AiLabelingResponse {
    // ai-service's LabelingResponse (the actual wire format returned by its /v1/label endpoint)
    // serializes these fields in snake_case via its own @JsonProperty annotations
    // (transaction_label, contract_address_label, taxable_reason) - these names were camelCase,
    // which Jackson silently maps to nothing on an unmatched key, leaving every field null on
    // every single call regardless of what ai-service actually returned. That null transactionLabel
    // was indistinguishable from a genuine AI failure, so every transaction - including ones
    // ai-service labeled correctly - fell through to markUnresolved() instead of ever getting
    // a real label persisted.
    @JsonProperty("transaction_label")
    private String transactionLabel;
    @JsonProperty("contract_address_label")
    private String contractAddressLabel;
    private String reason;
    private String taxable;
    @JsonProperty("taxable_reason")
    private String taxableReason;
}
