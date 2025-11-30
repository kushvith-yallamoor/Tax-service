package com.blockstats.tax.api.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

import static com.blockstats.commons.constants.HeaderConstants.X_CORRELATION_ID;
import static com.blockstats.commons.constants.HeaderConstants.X_REQUEST_DATE;

public class ResponseHeadersFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        filterChain.doFilter(request, response);
        response.addHeader(X_CORRELATION_ID, request.getHeader(X_CORRELATION_ID));
        response.addHeader(X_REQUEST_DATE, request.getHeader(X_REQUEST_DATE));
    }
}