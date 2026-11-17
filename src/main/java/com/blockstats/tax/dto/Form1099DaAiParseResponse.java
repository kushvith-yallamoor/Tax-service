package com.blockstats.tax.dto;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Form1099DaAiParseResponse {

	private String broker;
	private List<Form1099DaAiParsedLine> lines;

}
