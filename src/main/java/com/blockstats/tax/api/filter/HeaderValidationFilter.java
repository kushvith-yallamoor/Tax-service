package com.blockstats.tax.api.filter;

import com.blockstats.commons.exception.InvalidRequestException;
import com.blockstats.commons.exception.Violation;
import com.blockstats.commons.util.JsonParser;
import com.blockstats.error.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.blockstats.commons.constants.ErrorCodes.SERVICE_ERROR_00001;
import static com.blockstats.commons.constants.ErrorConstants.BLANK_HEADER_ERROR;
import static com.blockstats.commons.constants.HeaderConstants.*;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.HttpMethod.OPTIONS;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

public class HeaderValidationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        try {
            if (OPTIONS.matches(request.getMethod())) {
                filterChain.doFilter(request, response);
            } else {
                validateHeaders(request);
                filterChain.doFilter(request, response);
            }
        } catch (InvalidRequestException invalidRequestException) {
            ErrorResponse errorResponse = new ErrorResponse();
            errorResponse.setErrorCode(SERVICE_ERROR_00001);
            errorResponse.setErrorDescription("Bad Request, Incorrect or Missing Information.");
            if (Objects.nonNull(invalidRequestException.getViolations())) {
                errorResponse.setErrorMessages(
                        invalidRequestException
                                .getViolations()
                                .stream()
                                .map(violation -> violation.getName() + ":" + violation.getMessage())
                                .collect(Collectors.toList()));
            }
            response.setStatus(BAD_REQUEST.value());
            response.addHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE);
            response.getWriter().write(JsonParser.serialize(errorResponse));
        }
    }

    private void validateHeaders(HttpServletRequest request) {
        Set<Violation> violations = new HashSet<>();

        if (StringUtils.isBlank(request.getHeader(X_CORRELATION_ID))) {
            violations.add(new Violation(X_CORRELATION_ID, X_CORRELATION_ID + BLANK_HEADER_ERROR));
        }
        if (StringUtils.isBlank(request.getHeader(X_REQUEST_DATE))) {
            violations.add(new Violation(X_REQUEST_DATE, X_REQUEST_DATE + BLANK_HEADER_ERROR));
        }
        if (StringUtils.isBlank(request.getHeader(X_CLIENT_APP_ID))) {
            violations.add(new Violation(X_CLIENT_APP_ID, X_CLIENT_APP_ID + BLANK_HEADER_ERROR));
        }
        if (!violations.isEmpty()) {
            throw new InvalidRequestException(violations);
        }
    }
}