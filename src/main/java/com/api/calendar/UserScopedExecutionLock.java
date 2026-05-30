package com.api.calendar;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

@SuppressWarnings({"PMD.AtLeastOneConstructor", "PMD.OnlyOneReturn"})
@Component
public class UserScopedExecutionLock {

    private final ConcurrentMap<Long, ReentrantLock> userLocks = new ConcurrentHashMap<>();

    public void execute(final Long userId, final Runnable work) {
        execute(userId, () -> {
            work.run();
            return null;
        });
    }

    public <T> T execute(final Long userId, final Supplier<T> work) {
        if (userId == null) {
            return work.get();
        }

        final ReentrantLock lock = userLocks.computeIfAbsent(userId, ignored -> new ReentrantLock());
        lock.lock();
        try {
            return work.get();
        } finally {
            lock.unlock();
            if (!lock.hasQueuedThreads()) {
                userLocks.remove(userId, lock);
            }
        }
    }
}
