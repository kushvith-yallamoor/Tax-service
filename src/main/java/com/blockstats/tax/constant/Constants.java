package com.blockstats.tax.constant;

import static com.blockstats.commons.constants.AppConstants.UNSUPPORTED_OPERATION_MESSAGE;

/**
 * This class holds all constants in the application
 */
public class Constants {
    public static final String BASIC_TAX_SERVICE = "BASIC_TAX_SERVICE_";
    public static final String SUBSCRIPTION_TAX_SERVICE = "SUBSCRIPTION_TAX_SERVICE_";
    public static final String TRACKING_CLIENT_CONN_POOL = "TRACKING_CLIENT_CONN_POOL";
    public static final String TRACKING_WEB_CLIENT = "TRACKING_WEB_CLIENT";
    public static final String USER_CLIENT_CONN_POOL = "USER_CLIENT_CONN_POOL";
    public static final String USER_WEB_CLIENT = "USER_WEB_CLIENT";
    public static final String PAYMENT_CLIENT_CONN_POOL = "PAYMENT_CLIENT_CONN_POOL";
    public static final String PAYMENT_WEB_CLIENT = "PAYMENT_WEB_CLIENT";
    public static final String AI_CLIENT_CONN_POOL = "AI_CLIENT_CONN_POOL";
    public static final String AI_WEB_CLIENT = "AI_WEB_CLIENT";
    public static final String ROLE_POLICY_CACHE = "ROLE_POLICY_CACHE";
    public static final String ROLE_POLICY_SET_CACHE = "ROLE_POLICY_SET_CACHE";
    public static final String TAX_REFRESH_THREAD_POOL = "TAX_REFRESH_THREAD_POOL";

    private Constants() {
        throw new UnsupportedOperationException(UNSUPPORTED_OPERATION_MESSAGE);
    }
}
