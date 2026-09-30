package com.priceintel.backend.utils;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caps how often one caller may post to the public forms.
 *
 * <p>These endpoints take no token, so without this a script could fill the
 * tables overnight and bury the real enquiries. The limit is per key — the
 * caller's IP — and deliberately generous: a person filling in a form twice
 * because the first submit looked like it failed should not be refused.</p>
 *
 * <p>In memory, so it is per instance: behind a load balancer each node counts
 * separately, and a restart forgets everything. That is accepted here because
 * the cost of letting a few extra submissions through is a row in a table
 * somebody deletes, not a security hole. Anything stricter belongs at the
 * proxy, where the real client IP is known for certain.</p>
 */
public class SubmissionThrottle {

    private final int maxPerWindow;
    private final Duration window;
    private final Map<String, Window> seen = new ConcurrentHashMap<>();

    public SubmissionThrottle(int maxPerWindow, Duration window) {
        this.maxPerWindow = Math.max(1, maxPerWindow);
        this.window = window;
    }

    /** True when this key has room left; false when it should be turned away. */
    public boolean allow(String key) {
        if (key == null || key.isBlank()) {
            return true;    // Nothing to attribute it to; let it through rather than block everyone.
        }
        Instant now = Instant.now();
        prune(now);
        Window w = seen.compute(key, (k, existing) ->
                (existing == null || existing.startedAt.plus(window).isBefore(now))
                        ? new Window(now, 1)
                        : new Window(existing.startedAt, existing.count + 1));
        return w.count <= maxPerWindow;
    }

    /**
     * Drops windows that have expired. Without this the map grows by one entry
     * per address for as long as the process lives.
     */
    private void prune(Instant now) {
        if (seen.size() < 1000) {
            return;
        }
        seen.entrySet().removeIf(e -> e.getValue().startedAt.plus(window).isBefore(now));
    }

    private record Window(Instant startedAt, int count) { }
}
