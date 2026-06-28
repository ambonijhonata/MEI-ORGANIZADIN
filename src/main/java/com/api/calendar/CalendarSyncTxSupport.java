package com.api.calendar;

import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@SuppressWarnings("PMD.AtLeastOneConstructor")
public class CalendarSyncTxSupport {

    private TransactionTemplate txTemplate;

    public void configure(final PlatformTransactionManager txManager) {
        if (txManager != null) {
            this.txTemplate = new TransactionTemplate(txManager);
        }
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
