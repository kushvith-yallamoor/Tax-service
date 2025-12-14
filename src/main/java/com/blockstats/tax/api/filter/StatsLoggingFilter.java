package com.blockstats.tax.api.filter;

import com.blockstats.commons.stats.StatsCollector;
import com.blockstats.tax.stats.StatsLogger;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;

import static com.blockstats.commons.constants.StatsConstants.START_TIME;
import static com.blockstats.commons.constants.StatsConstants.TOTAL_RT;

@Slf4j
@AllArgsConstructor
public class StatsLoggingFilter extends OncePerRequestFilter {

    private final StatsLogger statsLogger;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        log.debug("Initiate StatsCollector");
        StatsCollector.newSession();
        StatsCollector.putInCurrentSession(START_TIME, Instant.now().toEpochMilli());
        filterChain.doFilter(request, response);
        long startTime = (Long) StatsCollector.getFromCurrentSession(START_TIME);
        StatsCollector.putInCurrentSession(TOTAL_RT, Instant.now().toEpochMilli() - startTime);
        this.statsLogger.logStats();
        StatsCollector.dispose();
        MDC.clear();
        log.debug("Dispose StatsCollector and MDC");
    }
}
