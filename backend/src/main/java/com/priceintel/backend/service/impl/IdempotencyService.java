package com.priceintel.backend.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;

import com.priceintel.backend.security.TenantContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Runs an action once, however many times it is asked for (§12.2).
 *
 * <p>Guards the operations that spend something unrecoverable: a marketplace
 * call against a shared quota, an AI call against a per-minute token budget, a
 * change to a selling price. At the HTTP layer a double-click, a browser retry
 * and a deliberate second request look identical, so the distinction has to be
 * recorded rather than inferred.</p>
 *
 * <h2>Two ways to identify a request</h2>
 * <ul>
 *   <li>An {@code Idempotency-Key} header from the client — exact, and lets a
 *       user deliberately repeat an action by sending a new key.</li>
 *   <li>Failing that, a fingerprint of tenant, action and arguments inside a
 *       short window. Less precise, but it asks nothing of the caller and
 *       catches the case that actually happens.</li>
 * </ul>
 *
 * <p>Deliberately not applied to reads, or to anything cheap and reversible:
 * the protection costs a row and a round trip, and blocking a repeated harmless
 * action is its own kind of bug.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    /**
     * How long a fingerprint counts as the same request.
     *
     * <p>Long enough to cover a slow call and an impatient second click, short
     * enough that a genuine repeat a minute later is honoured. Applies only when
     * the client sends no key — an explicit key is respected however old.</p>
     */
    private static final Duration FINGERPRINT_WINDOW = Duration.ofSeconds(45);

    private final IdempotencyStore store;

    /**
     * Executes {@code action} once per key.
     *
     * @param scope       the action, e.g. {@code SEARCH_JOB}
     * @param headerKey   the client's Idempotency-Key, or null
     * @param fingerprint arguments identifying this request when no header is sent
     * @param type        the response type, for reading a stored replay back
     * @return the action's result, or the first call's result on a replay
     */
    public <T> T execute(String scope, String headerKey, String fingerprint,
            Class<T> type, Supplier<T> action) {
        String key = headerKey != null && !headerKey.isBlank()
                ? headerKey.trim()
                : fingerprintKey(scope, fingerprint);

        Optional<T> replay = store.claimOrReplay(scope, key, type);
        if (replay.isPresent()) {
            log.info("Idempotent replay of {} for key {} — no work done", scope, shorten(key));
            return replay.get();
        }

        try {
            T result = action.get();
            store.store(scope, key, result);
            return result;
        } catch (RuntimeException e) {
            // A failed attempt must not block a retry: the caller is entitled to
            // try again, and the claim would otherwise hold the key indefinitely.
            store.release(scope, key);
            throw e;
        }
    }

    /**
     * A key derived from the request itself, bucketed into a time window.
     *
     * <p>The window is part of the hash rather than a comparison, so the same
     * request in the next window produces a different key and runs again — no
     * expiry check, and no sweep needed before a repeat becomes possible.</p>
     */
    private String fingerprintKey(String scope, String fingerprint) {
        long bucket = System.currentTimeMillis() / FINGERPRINT_WINDOW.toMillis();
        String raw = scope + '|' + TenantContext.getTenantId() + '|'
                + (fingerprint == null ? "" : fingerprint) + '|' + bucket;
        return "auto-" + sha256(raw);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of()
                    .formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)))
                    .substring(0, 40);
        } catch (Exception e) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private String shorten(String key) {
        return key.length() > 16 ? key.substring(0, 16) + "…" : key;
    }
}
