package com.blockstats.tax.bo;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class PaymentBO {

	private long userType;
	private String country;
	private String correlationId;
	private String requestDate;

}
