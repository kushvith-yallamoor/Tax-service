package com.blockstats.tax.api.authorization;

import com.blockstats.commons.api.authorization.bean.UserContext;
import com.blockstats.commons.util.AuthUtil;
import com.blockstats.tax.delegate.AccountantAccessCheckDelegate;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Tax-side counterpart to wallet-service/aggregator-service/performance-service's
 * AccountantActingAsInterceptor - identical mechanism, same X-Acting-As-User-Id header, same
 * FULL-access requirement. Separate from the existing read-only AccountantAccessCheckDelegate
 * usage in TaxController#getClientTaxSummaryForAccountant (that's VIEW_ONLY-level, deliberate
 * single-purpose endpoint) - this one makes the REGULAR Tax Center endpoints also work correctly
 * while an accountant is acting as a client, same as every other page.
 *
 * Unlike every other service, tax-service's controllers are a mix of AuthUtil.getUserContext(request)
 * (fixed by mutating the request-attribute UserContext below, same as elsewhere) AND
 * @AuthenticationPrincipal UserContext (TaxController, Form1099DaController, AITxnLabellingController)
 * - that second group is populated straight from the raw gateway header by
 * AuthorizationRolesSetupFilter into a SEPARATE UserContext instance held in Spring Security's
 * SecurityContextHolder, which this interceptor would otherwise never touch. Also mutating that
 * instance here (it's the same mutable class, just a different object) fixes every
 * @AuthenticationPrincipal endpoint in one place instead of hunting down each controller method.
 */
@Slf4j
@AllArgsConstructor
public class AccountantActingAsInterceptor implements HandlerInterceptor {

    public static final String X_ACTING_AS_USER_ID = "X-Acting-As-User-Id";

    private final AccountantAccessCheckDelegate accountantAccessCheckDelegate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String actingAsUserId = request.getHeader(X_ACTING_AS_USER_ID);
        if (StringUtils.isBlank(actingAsUserId)) {
            return true;
        }

        // The request-attribute UserContext (AuthUtil) only exists for @AuthorizeUser-annotated
        // handlers - TaxController/Form1099DaController/AITxnLabellingController use
        // @AuthenticationPrincipal instead, which never populates that attribute at all, so
        // AuthUtil.getUserContext(request) always throws here for those. The SecurityContext
        // principal set by AuthorizationRolesSetupFilter is unconditional for every request on
        // this service, so that's the one reliable source for the caller's real identity.
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserContext securityUserContext = authentication != null && authentication.getPrincipal() instanceof UserContext uc
                ? uc : null;

        UserContext attributeUserContext;
        try {
            attributeUserContext = AuthUtil.getUserContext(request);
        } catch (Exception e) {
            attributeUserContext = null;
        }

        UserContext callerContext = attributeUserContext != null ? attributeUserContext : securityUserContext;
        if (callerContext == null) {
            return true;
        }

        String accountantUserId = callerContext.getUserId();
        if (StringUtils.equals(accountantUserId, actingAsUserId)) {
            return true;
        }

        boolean allowed = accountantAccessCheckDelegate.hasFullAccess(accountantUserId, actingAsUserId);
        if (!allowed) {
            log.warn("Accountant {} attempted to act as client {} without FULL access", accountantUserId, actingAsUserId);
            response.setStatus(HttpStatus.FORBIDDEN.value());
            return false;
        }

        if (attributeUserContext != null) {
            attributeUserContext.setUserId(actingAsUserId);
        }
        if (securityUserContext != null) {
            securityUserContext.setUserId(actingAsUserId);
        }

        return true;
    }
}
