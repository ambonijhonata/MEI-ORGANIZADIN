package com.api.calendar;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public record CalendarSyncBatchSettings(
        int batchSize,
        boolean batchClearEnabled,
        int flushInterval
) {
    private static final int DEFAULT_SIZE = 200;
    private static final int DEFAULT_FLUSH = 1;

    public CalendarSyncBatchSettings(
            @Value("${calendar.sync.batch-size:" + DEFAULT_SIZE + "}") final int batchSize,
            @Value("${calendar.sync.batch-clear-enabled:false}") final boolean batchClearEnabled,
            @Value("${calendar.sync.batch-flush-every-chunks:" + DEFAULT_FLUSH + "}") final int flushInterval
    ) {
        this.batchSize = batchSize <= 0 ? DEFAULT_SIZE : batchSize;
        this.batchClearEnabled = batchClearEnabled;
        this.flushInterval = flushInterval <= 0 ? DEFAULT_FLUSH : flushInterval;
    }
}
