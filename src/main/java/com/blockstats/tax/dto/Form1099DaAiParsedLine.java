package com.blockstats.tax.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Form1099DaAiParsedLine {

	private String source;
	private String asset;
	private Double unitsSold;
	private Double proceeds;
	private Double costBasis;
	private String dateAcquired;
	private String dateSold;

}
