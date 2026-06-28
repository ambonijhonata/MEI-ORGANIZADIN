package com.api.calendar;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@SuppressWarnings("PMD.LongVariable")
public record CalendarSyncBatchSettings(
        int batchSize,
        boolean batchClearEnabled,
        int batchFlushEveryChunks
) {
    private static final int DEFAULT_BATCH_SIZE = 200;
    private static final int DEFAULT_FLUSH_EVERY_CHUNKS = 1;

    public CalendarSyncBatchSettings(
            @Value("${calendar.sync.batch-size:" + DEFAULT_BATCH_SIZE + "}") final int batchSize,
            @Value("${calendar.sync.batch-clear-enabled:false}") final boolean batchClearEnabled,
            @Value("${calendar.sync.batch-flush-every-chunks:" + DEFAULT_FLUSH_EVERY_CHUNKS + "}") final int batchFlushEveryChunks
    ) {
        this.batchSize = batchSize <= 0 ? DEFAULT_BATCH_SIZE : batchSize;
        this.batchClearEnabled = batchClearEnabled;
        this.batchFlushEveryChunks = batchFlushEveryChunks <= 0 ? DEFAULT_FLUSH_EVERY_CHUNKS : batchFlushEveryChunks;
    }
}
