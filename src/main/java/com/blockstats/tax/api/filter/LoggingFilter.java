package com.blockstats.tax.api.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class LoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        if (log.isInfoEnabled()) {
            printRequestAndResponse(request, response, filterChain);
        } else {
            filterChain.doFilter(request, response);
        }
    }

    private void printRequestAndResponse(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        ContentCachingRequestWrapper cachingRequestWrapper = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper cachingResponseWrapper = new ContentCachingResponseWrapper(response);
        try {
            filterChain.doFilter(cachingRequestWrapper, cachingResponseWrapper);
        } catch (Exception e) {
            log.error("Error while logging SEAR request/response", e);
            throw e;
        } finally {
            try {
                try {
                    logRequest(cachingRequestWrapper);
                } finally {
                    logResponse(cachingResponseWrapper);
                }
            } catch (Exception e) {
                log.error("Error while logging SEAR request/response", e);
            }
        }
        cachingResponseWrapper.copyBodyToResponse();
    }

    private void logRequest(ContentCachingRequestWrapper cachingRequestWrapper) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("\nBase Url:").append(cachingRequestWrapper.getMethod() + " : " + cachingRequestWrapper.getRequestURI());
        Map<String, String> headersMap = Collections.list(cachingRequestWrapper.getHeaderNames())
                .stream()
                .collect(Collectors.toMap(headerName -> headerName,
                        cachingRequestWrapper::getHeader,
                        (oldValue, newValue) -> newValue));
        stringBuilder.append("\nRequest Headers:").append(headersMap);
        stringBuilder.append("\nRequest Body:").append(contentString(cachingRequestWrapper.getContentAsByteArray(), cachingRequestWrapper.getCharacterEncoding()));
        log.debug("\n--------------------------Input Request-----------------{}", stringBuilder);
    }

    private String contentString(byte[] contentAsByteArray, String characterEncoding) {
        String body;
        try {
            body = new String(contentAsByteArray, characterEncoding);
        } catch (UnsupportedEncodingException e) {
            log.error("Provided encoding is invalid. Retrying with UTF_8", e);
            body = new String(contentAsByteArray, StandardCharsets.UTF_8);
        }
        return StringUtils.isNotBlank(body) ? body : "No Body Attached";
    }

    private void logResponse(ContentCachingResponseWrapper cachingResponseWrapper) {
        StringBuilder stringBuilder = new StringBuilder();
        Map<String, String> headersMap = cachingResponseWrapper.getHeaderNames()
                .stream()
                .collect(Collectors.toMap(headerName -> headerName,
                        cachingResponseWrapper::getHeader,
                        (oldValue, newValue) -> newValue));
        stringBuilder.append("\nResponse Headers:").append(headersMap);
        stringBuilder.append("\nResponse Body:").append(contentString(cachingResponseWrapper.getContentAsByteArray(), cachingResponseWrapper.getCharacterEncoding()));
        log.debug("\n--------------------------Output Response-----------------{}", stringBuilder);
    }

}