package com.priceintel.backend.dto.request;

import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Everything the search planner can use to find a product (FR-SRCH-001).
 *
 * <p>Replaces a single {@code query} string. The planner needs to know what each
 * value <em>is</em> — an ASIN is an exact key, a title is a guess — because the
 * order it tries them in is the difference between one right answer and a page
 * of plausible wrong ones. A bare string cannot carry that.</p>
 *
 * <p>Every field is optional; supply what you have. The one rule is that at
 * least one of identifiers, title or url must be present, or there is nothing to
 * search for.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelSearchRequest {

    /**
     * Identifiers keyed by type — {@code ASIN}, {@code UPC}, {@code EAN},
     * {@code GTIN}, {@code MPN}, {@code SKU}.
     *
     * <p>A map rather than named fields so a new identifier type does not
     * require a contract change. SKU is accepted and deliberately never sent to
     * a marketplace: it is the seller's private code and means nothing to
     * anybody else. It is kept so a caller can pass its whole record without
     * filtering, and so a result can be reported against it.</p>
     */
    private Map<String, String> identifiers;

    @Size(max = 500)
    private String title;

    @Size(max = 200)
    private String brand;

    /**
     * A pasted marketplace product URL. The identifier is extracted server-side
     * and used ahead of the title, since a link is an exact reference.
     */
    @Size(max = 2000)
    private String url;

    /**
     * An image to match against the catalogue, as a data URI or an http(s) URL.
     *
     * <p>Matched against product images we already hold. No marketplace offers
     * image search, so this cannot reach outward — it finds an existing product,
     * whose identifiers then drive the marketplace call.</p>
     */
    private String image;

    /** Marketplaces to search. Empty means every enabled one. */
    private List<String> markets;

    /**
     * A country to prefer, as an ISO code — {@code US}, {@code IN}, {@code GB}.
     *
     * <p>A product sold only in one country is missing everywhere else, and
     * "not found" then says more about where we looked than about the product.</p>
     */
    @Size(max = 5)
    private String region;

    /**
     * Where the buyer is, so delivery can be priced (§13.1 "Destination").
     *
     * <p>Distinct from {@link #region}, which chooses <em>whose catalogue</em> to
     * search. This chooses <em>where it ships to</em>. Searching amazon.co.uk and
     * delivering to Manchester are two different facts, and only the second one
     * decides what the buyer pays.</p>
     */
    @jakarta.validation.Valid
    private Destination destination;

    /**
     * After an identifier resolves, also search its title for rival products.
     *
     * <p>An identifier answers "what is this" — one ASIN is one page, so an ASIN
     * search returns exactly one listing and never a competitor. This asks the
     * second question too, using the title Amazon just returned for that code.</p>
     *
     * <p>Off by default because it costs a second marketplace call and judges
     * roughly twelve listings instead of one. Worth turning on for an
     * interactive search, where finding rivals is the point; worth leaving off
     * for a bulk run, where it multiplies both quotas by ten.</p>
     */
    private boolean includeSimilar;

    @Size(max = 20)
    private String condition;

    private Integer maxResults;

    /** Bypass the cache and re-fetch live. */
    private boolean forceRefresh;

    /**
     * Whether to judge each result against what was searched for.
     *
     * <p>Off by default because judging costs a metered AI call and a caller
     * that only wants to see what exists should not pay for it. On, the response
     * carries {@code matches} and {@code rejected} alongside the trace.</p>
     *
     * <p>Worth turning on even for an exact identifier search. An ASIN says how
     * a candidate was found, not that it is acceptable: the same identifier
     * family can return a two-pack when the product is a single, or a renewed
     * unit when the product is new. Those are the cases the judging exists to
     * catch, and skipping it for identifier searches would skip it exactly where
     * the mistake is most expensive.</p>
     */
    private boolean judge;

    /** Whether unpriced results should be dropped. Defaults to true. */
    private Boolean requirePrice;

    public boolean isPriceRequired() {
        return !Boolean.FALSE.equals(requirePrice);
    }

    /** True when there is something to search with. */
    public boolean hasAnyInput() {
        boolean hasIdentifier = identifiers != null && identifiers.values().stream()
                .anyMatch(v -> v != null && !v.isBlank());
        return hasIdentifier
                || (title != null && !title.isBlank())
                || (url != null && !url.isBlank())
                || (image != null && !image.isBlank());
    }
}
