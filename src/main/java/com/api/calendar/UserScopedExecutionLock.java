package com.api.calendar;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

@Component
public class UserScopedExecutionLock {

    private final ConcurrentMap<Long, ReentrantLock> userLocks = new ConcurrentHashMap<>();

    public void execute(Long userId, Runnable work) {
        execute(userId, () -> {
            work.run();
            return null;
        });
    }

    public <T> T execute(Long userId, Supplier<T> work) {
        if (userId == null) {
            return work.get();
        }

        ReentrantLock lock = userLocks.computeIfAbsent(userId, ignored -> new ReentrantLock());
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
