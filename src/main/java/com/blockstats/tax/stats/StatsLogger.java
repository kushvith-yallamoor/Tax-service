package com.blockstats.tax.stats;

import com.blockstats.commons.stats.StatsLogMessageBuilder;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.springframework.stereotype.Service;

import static com.blockstats.commons.constants.StatsConstants.*;

@Service
@Slf4j
public class StatsLogger {

    private static final Marker TAXSTATS = MarkerFactory.getMarker("TAXSTATS");

    public void logStats() {
        String statsMessage = StatsLogMessageBuilder.buildStatsMessage(TOTAL_RT, EVENT, FINANCIAL_YEAR, FILE_NAME);
        log.error(TAXSTATS, statsMessage);
    }
}
