package com.priceintel.backend.marketplace.amazon;

/**
 * A minimal token-bucket-style limiter that enforces a minimum interval between
 * calls (SP-API uses per-operation rate limits). Blocks the caller briefly when
 * calls come too fast.
 */
public class SimpleRateLimiter {

    private final long minIntervalMs;
    private long lastCallTime = 0L;

    public SimpleRateLimiter(double permitsPerSecond) {
        double rps = permitsPerSecond <= 0 ? 1 : permitsPerSecond;
        this.minIntervalMs = (long) (1000.0 / rps);
    }

    public synchronized void acquire() {
        long now = System.currentTimeMillis();
        long waitMs = (lastCallTime + minIntervalMs) - now;
        if (waitMs > 0) {
            try {
                Thread.sleep(waitMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        lastCallTime = System.currentTimeMillis();
    }
}
