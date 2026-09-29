package com.priceintel.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.priceintel.backend.constants.IdentifierType;
import com.priceintel.backend.exception.BadRequestException;
import com.priceintel.backend.marketplace.Marketplace;

/**
 * Accepts enum path and query values in any case.
 *
 * <p>Spring's default enum binding is exact-match, so {@code /marketplaces/amazon/...}
 * fails while {@code /marketplaces/AMAZON/...} succeeds — and it fails as a type
 * mismatch, which surfaces to the user as "An unexpected error occurred" with no
 * hint that the case was the problem. Marketplace names appear in URLs, links and
 * hand-written requests, where lower case is the natural form.</p>
 *
 * <p>Fixed here rather than in each caller: one converter covers every endpoint,
 * present and future, whereas asking clients to upper-case leaves the next one
 * to rediscover the same failure.</p>
 */
@Configuration
public class EnumConverterConfig implements WebMvcConfigurer {

    /**
     * Registered with explicit source and target classes.
     *
     * <p>The single-argument {@code addConverter} reads the pair from the
     * converter's generics, which a type variable erases — the converter then
     * registers against {@code Enum} rather than the concrete enum, Spring's
     * built-in exact-match factory keeps winning, and lower case still fails.
     * Naming the classes sidesteps the introspection entirely.</p>
     */
    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(String.class, Marketplace.class,
                source -> resolve(Marketplace.class, source, "marketplace"));
        registry.addConverter(String.class, IdentifierType.class,
                source -> resolve(IdentifierType.class, source, "identifier type"));
    }

    /** Resolves an enum constant ignoring case, and names the valid options when it cannot. */
    private static <E extends Enum<E>> E resolve(Class<E> type, String source, String label) {
        if (source == null || source.isBlank()) {
            return null;
        }
        String needle = source.trim();
        for (E constant : type.getEnumConstants()) {
            if (constant.name().equalsIgnoreCase(needle)) {
                return constant;
            }
        }
        // A named 400 rather than the framework's type-mismatch 500: the caller
        // can act on "expected AMAZON, EBAY" but not on a stack trace.
        throw new BadRequestException("Unknown " + label + ": '" + source + "'. Expected one of "
                + String.join(", ", java.util.Arrays.stream(type.getEnumConstants())
                        .map(Enum::name).toList()) + ".");
    }
}
