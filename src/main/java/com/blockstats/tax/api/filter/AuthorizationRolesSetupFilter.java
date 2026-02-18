package com.blockstats.tax.api.filter;

import com.blockstats.commons.api.authorization.bean.UserContext;
import com.blockstats.commons.util.JsonParser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.blockstats.commons.constants.HeaderConstants.X_APIGATEWAY_API_USERINFO;
import static org.springframework.http.HttpMethod.OPTIONS;

public class AuthorizationRolesSetupFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        if (OPTIONS.matches(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }
        String apiGatewayUserContext = request.getHeader(X_APIGATEWAY_API_USERINFO);
        if (StringUtils.isBlank(apiGatewayUserContext)) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.getWriter().write("Error authenticating the user request.");
            return;
        }
        UserContext userContext = JsonParser.deSerialize(new String(Base64.getUrlDecoder().decode(apiGatewayUserContext)), UserContext.class);
        if (Objects.isNull(userContext) || StringUtils.isBlank(userContext.getUserId())) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.getWriter().write("Error authenticating the user request.");
            return;
        }

        List<GrantedAuthority> grantedAuthorities = new ArrayList<>();

        if (CollectionUtils.isNotEmpty(userContext.getRoles())) {
            grantedAuthorities =
                    userContext
                            .getRoles()
                            .stream()
                            .filter(StringUtils::isNotEmpty)
                            .map(role -> "ROLE_" + StringUtils.toRootUpperCase(role))
                            .map(SimpleGrantedAuthority::new)
                            .collect(Collectors.toList());
        }

        Authentication authentication = new UsernamePasswordAuthenticationToken(userContext, null, grantedAuthorities);

        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }
}
