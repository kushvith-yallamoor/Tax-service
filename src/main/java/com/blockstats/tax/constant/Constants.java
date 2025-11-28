package com.blockstats.tax.constant;

import static com.blockstats.commons.constants.AppConstants.UNSUPPORTED_OPERATION_MESSAGE;

/**
 * This class holds all constants in the application
 */
public class Constants {

	public static final String COIN = "coin";
	public static final String FIFO = "FIFO";
	public static final String LIFO = "LIFO";
	public static final String HIFO = "HIFO";
	public static final String AVG = "AVG";
	public static final String TAX_SERVICE = "TAX_SERVICE_";

	private Constants() {
		throw new UnsupportedOperationException(UNSUPPORTED_OPERATION_MESSAGE);
	}

}
