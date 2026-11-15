package com.foresight.fcms.auth.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class AuthRateLimitService {
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public boolean allow(String key, int limit) {
        long bucket = System.currentTimeMillis() / 900000;
        if (windows.size() > 10000) {
            windows.entrySet().removeIf(e -> e.getValue().bucket < bucket);
            if (windows.size() > 10000) return false;
        }
        var w =
                windows.compute(
                        key,
                        (k, old) -> old == null || old.bucket != bucket ? new Window(bucket) : old);
        return w.count.incrementAndGet() <= limit;
    }

    private static class Window {
        final long bucket;
        final AtomicInteger count = new AtomicInteger();

        Window(long b) {
            bucket = b;
        }
    }
}
