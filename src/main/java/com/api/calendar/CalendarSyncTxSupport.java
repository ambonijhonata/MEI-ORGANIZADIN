package com.api.calendar;

import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class CalendarSyncTxSupport {

    private final TransactionTemplate txTemplate;

    @Autowired
    public CalendarSyncTxSupport(final PlatformTransactionManager txManager) {
        this(txManager == null ? null : new TransactionTemplate(txManager));
    }

    /* default */ // for non-Spring tests that do not need a real transaction manager.
    CalendarSyncTxSupport() {
        this((TransactionTemplate) null);
    }

    private CalendarSyncTxSupport(final TransactionTemplate txTemplate) {
        this.txTemplate = txTemplate;
    }

    public void run(final Runnable work) {
        if (txTemplate == null) {
            work.run();
        } else {
            txTemplate.executeWithoutResult(status -> work.run());
        }
    }

    public <T> T get(final Supplier<T> work) {
        final T result;
        if (txTemplate == null) {
            result = work.get();
        } else {
            result = txTemplate.execute(status -> work.get());
        }
        return result;
    }
}
