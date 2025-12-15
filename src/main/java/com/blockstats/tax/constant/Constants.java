package com.blockstats.tax.constant;

import static com.blockstats.commons.constants.AppConstants.UNSUPPORTED_OPERATION_MESSAGE;

/**
 * This class holds all constants in the application
 */
public class Constants {
    public static final String TAX_SERVICE = "TAX_SERVICE_";
    public static final String TRACKING_CLIENT_CONN_POOL = "TRACKING_CLIENT_CONN_POOL";
    public static final String TRACKING_WEB_CLIENT = "TRACKING_WEB_CLIENT";
    public static final String USER_CLIENT_CONN_POOL = "USER_CLIENT_CONN_POOL";
    public static final String USER_WEB_CLIENT = "USER_WEB_CLIENT";

    private Constants() {
        throw new UnsupportedOperationException(UNSUPPORTED_OPERATION_MESSAGE);
    }
}
