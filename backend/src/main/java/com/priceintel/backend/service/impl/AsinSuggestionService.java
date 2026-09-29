package com.priceintel.backend.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.priceintel.backend.ai.AiCompletion;
import com.priceintel.backend.ai.AiCompletionRequest;
import com.priceintel.backend.ai.AiGateway;
import com.priceintel.backend.ai.OpenAiProperties;
import com.priceintel.backend.constants.IdentifierType;
import com.priceintel.backend.dto.response.AsinLookupResponse;
import com.priceintel.backend.dto.response.AsinSuggestion;
import com.priceintel.backend.entity.Product;
import com.priceintel.backend.exception.AiException;
import com.priceintel.backend.exception.BadRequestException;
import com.priceintel.backend.marketplace.Marketplace;
import com.priceintel.backend.repository.ProductIdentifierRepository;
import com.priceintel.backend.repository.ProductRepository;
import com.priceintel.backend.security.TenantContext;
import com.priceintel.backend.service.MarketplaceService;
import com.priceintel.backend.utils.IdentifierValidator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Finds the real ASIN for a product we only know by name.
 *
 * <p>Exists because of a specific failure: seventeen of this catalogue's ASINs
 * turned out to be invented — {@code B0D9A12345}, {@code B0BLEG4215} — and every
 * one of them made searches return neighbours instead of the product. Correcting
 * them by hand means finding each on Amazon and copying the code back.</p>
 *
 * <h2>Why a model is allowed near this at all</h2>
 * <p>A web-searching model is genuinely good at "which Amazon listing is the
 * Nike Air Zoom Pegasus 41", and genuinely bad at what it costs — asked for
 * both, it returned the correct ASIN and a price 22% out of date. So it is used
 * for identification only, and <em>every</em> suggestion is looked up on the
 * marketplace before being shown. An ASIN that does not resolve is discarded
 * silently, whatever the model claimed.</p>
 *
 * <p>Nothing here writes to the catalogue. Suggestions are returned for a person
 * to accept, because a wrong identifier written automatically is exactly the
 * problem this was built to clean up.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsinSuggestionService {

    /** ASINs anywhere in the model's reply, whatever prose surrounds them. */
    private static final Pattern ASIN_PATTERN = Pattern.compile("\\bB0[A-Z0-9]{8}\\b");

    /**
     * Kept deliberately short.
     *
     * <p>This model searches the web and accumulates the pages it reads into the
     * same request, so the prompt and the evidence compete for one budget. A
     * fuller instruction — the same rules spelled out over three paragraphs —
     * returned HTTP 413 on every title query while this one succeeds; the words
     * saved here are the room the search needs.</p>
     */
    private static final String SYSTEM_PROMPT =
            "Reply with Amazon.com ASINs only, one per line, at most 3, best match first. "
            + "No prices, titles or explanation. If not found, reply: NONE";

    /**
     * Identifier types worth giving the model, most specific first.
     *
     * <p>ASIN is absent deliberately. When one is present and correct there is
     * nothing to find, and when it is wrong — the case this exists for — it is a
     * fabricated string that would only mislead the search.</p>
     */
    private static final List<IdentifierType> LOOKUP_PRIORITY =
            List.of(IdentifierType.UPC, IdentifierType.EAN, IdentifierType.GTIN,
                    IdentifierType.MPN);

    private final AiGateway aiGateway;
    private final OpenAiProperties props;
    private final ProductRepository productRepo;
    private final ProductIdentifierRepository identifierRepo;
    private final MarketplaceService marketplaceService;

    public boolean isAvailable() {
        return props.isWebSearchAvailable() && aiGateway.isAvailable();
    }

    /**
     * Suggests verified ASINs for a product.
     *
     * @return candidates that exist on Amazon, each with the title and price the
     *         marketplace reports — never what the model said they were
     */
    @Transactional(readOnly = true)
    public AsinLookupResponse suggestFor(Long productId) {
        if (!isAvailable()) {
            return notConfigured();
        }
        Product product = productRepo.findById(productId)
                .filter(p -> {
                    Long tenantId = TenantContext.getTenantId();
                    return tenantId == null || tenantId.equals(p.getTenantId());
                })
                .orElseThrow(() -> new BadRequestException("Product not found: " + productId));

        try {
            return verifyAll(askModel(describe(product)), product);
        } catch (RuntimeException e) {
            return failureResponse(e);
        }
    }

    // ---------- the model ----------

    /**
     * Asks the model, letting the failure reason through.
     *
     * <p>Failures are not swallowed into an empty list here: the caller has to
     * be able to tell "no such product" from "the provider refused", because the
     * user is told a different thing in each case.</p>
     */
    private List<String> askModel(String description) {
        AiCompletion completion = aiGateway.complete(AiCompletionRequest.builder()
                .systemPrompt(SYSTEM_PROMPT)
                .userPrompt(description)
                .temperature(0.0)
                // Not JSON mode: the web-search models do not all support it,
                // and one ASIN per line needs no structure to parse.
                .jsonOnly(false)
                // Small on purpose. These models accumulate the pages they read
                // into the request, and an over-large budget is what produced
                // the 413s — three ASINs need very little room.
                .maxTokens(120)
                .model(props.getWebSearchModel())
                .build());
        return extractAsins(completion.getContent());
    }

    /** Turns a provider failure into something the user can act on. */
    private AsinLookupResponse failureResponse(RuntimeException e) {
        AiException.Reason reason = e instanceof AiException ai
                ? ai.getReason() : AiException.Reason.PROVIDER_ERROR;
        log.warn("ASIN lookup failed ({}): {}", reason, e.getMessage());

        return switch (reason) {
            case RATE_LIMITED -> AsinLookupResponse.builder()
                    .suggestions(List.of()).status("RATE_LIMITED").retryable(true)
                    .message("The AI service is busy. Wait a minute and try again — "
                            + "this is not a result about your product.")
                    .build();
            case PAYLOAD_TOO_LARGE -> AsinLookupResponse.builder()
                    .suggestions(List.of()).status("UNAVAILABLE").retryable(true)
                    .message("The search returned more than the AI service could handle. "
                            + "Try a shorter or more specific product name.")
                    .build();
            case AUTHENTICATION -> AsinLookupResponse.builder()
                    .suggestions(List.of()).status("UNAVAILABLE").retryable(false)
                    .message("The AI service rejected our credentials. Check the API key.")
                    .build();
            default -> AsinLookupResponse.builder()
                    .suggestions(List.of()).status("UNAVAILABLE").retryable(true)
                    .message("The AI lookup could not run. Nothing was searched, so this says "
                            + "nothing about your product.")
                    .build();
        };
    }

    /**
     * Everything known about the product, phrased for a search.
     *
     * <p>Each identifier is labelled rather than run together, so the model can
     * tell a barcode from a part number and search accordingly. Sending them all
     * beats picking one: a barcode pins the exact item, and a title supplies the
     * words that make a web search work at all — the combination is stronger
     * than either.</p>
     */
    private String describe(Product product) {
        StringBuilder sb = new StringBuilder();
        String brand = product.getBrand();
        if (brand != null && !brand.isBlank() && product.getTitle() != null
                && !product.getTitle().toLowerCase().contains(brand.toLowerCase())) {
            sb.append(brand).append(' ');
        }
        sb.append(product.getTitle() == null ? "" : product.getTitle());

        // One identifier, not all of them. Each extra line is context the search
        // itself needs, and a barcode plus a part number plus a title is no more
        // findable than the single most specific of the three.
        var identifiers = identifierRepo.findByProductId(product.getId());
        for (IdentifierType type : LOOKUP_PRIORITY) {
            var match = identifiers.stream()
                    .filter(i -> i.getType() == type)
                    .filter(i -> i.getOriginalValue() != null && !i.getOriginalValue().isBlank())
                    .findFirst();
            if (match.isPresent()) {
                sb.append(" (").append(label(type)).append(' ')
                  .append(match.get().getOriginalValue()).append(')');
                break;
            }
        }
        return sb.toString();
    }

    /**
     * Finds ASINs from a single value the user typed, with no saved product
     * behind it.
     *
     * @param query the barcode, part number or product name
     * @param type  what it is, or null to let the model infer it
     */
    @Transactional(readOnly = true)
    public AsinLookupResponse suggestFor(String query, String type) {
        if (!isAvailable()) {
            return notConfigured();
        }
        if (query == null || query.isBlank()) {
            throw new BadRequestException("Enter a barcode, part number or product name.");
        }
        String described = type == null || type.isBlank()
                ? query.trim()
                : label(type.trim().toUpperCase()) + ": " + query.trim();

        try {
            return verifyAll(askModel(described), null);
        } catch (RuntimeException e) {
            return failureResponse(e);
        }
    }

    /**
     * Checks every proposed ASIN against Amazon and describes the outcome.
     *
     * <p>{@code proposedCount} is reported alongside the survivors because the
     * two differing is the interesting case: the model named products that do
     * not exist in the marketplaces we can reach, which usually means the
     * product is sold somewhere we are not authorised for.</p>
     */
    private AsinLookupResponse verifyAll(List<String> candidates, Product product) {
        List<AsinSuggestion> verified = new ArrayList<>();
        for (String asin : candidates) {
            verify(product, asin).ifPresent(verified::add);
        }
        log.info("ASIN lookup: {} proposed, {} verified on Amazon",
                candidates.size(), verified.size());

        if (!verified.isEmpty()) {
            return AsinLookupResponse.builder()
                    .suggestions(verified).status("FOUND")
                    .proposedCount(candidates.size())
                    .message(verified.size() + " listing(s) found on Amazon. "
                            + "Check the titles match your product before using one.")
                    .build();
        }
        return AsinLookupResponse.builder()
                .suggestions(List.of()).status("NONE")
                .proposedCount(candidates.size())
                .message(candidates.isEmpty()
                        ? "No matching Amazon listing found. The identifier may be wrong, or the "
                                + "product may not be sold in the regions we can search."
                        : candidates.size() + " candidate(s) were suggested but none exists on "
                                + "Amazon US or CA — the product is likely sold only in a "
                                + "marketplace we are not authorised for.")
                .build();
    }

    private AsinLookupResponse notConfigured() {
        return AsinLookupResponse.builder()
                .suggestions(List.of()).status("UNAVAILABLE").retryable(false)
                .message("AI lookup is not set up. Configure a web-search model to use it.")
                .build();
    }

    private String label(IdentifierType type) {
        return label(type.name());
    }

    /** Names the identifier so the model searches for it as one, not as free text. */
    private String label(String type) {
        return switch (type) {
            case "UPC" -> "UPC barcode";
            case "EAN" -> "EAN barcode";
            case "GTIN" -> "GTIN barcode";
            case "MPN" -> "Manufacturer part number";
            case "ASIN" -> "Amazon ASIN";
            case "TITLE", "NAME" -> "Product name";
            case "BRAND" -> "Brand";
            case "SKU" -> "Seller's own SKU (not an Amazon identifier)";
            default -> type;
        };
    }

    /**
     * Pulls ASINs out of the reply.
     *
     * <p>Pattern-matched rather than parsed: models add prose however firmly the
     * prompt forbids it, and a stray sentence should not cost a usable answer.
     * Order is preserved, since the model was asked for best match first.</p>
     */
    private List<String> extractAsins(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        Set<String> found = new LinkedHashSet<>();
        Matcher m = ASIN_PATTERN.matcher(content.toUpperCase());
        while (m.find() && found.size() < 3) {
            String asin = m.group();
            if (IdentifierValidator.validate(IdentifierType.ASIN, asin).isEmpty()) {
                found.add(asin);
            }
        }
        return new ArrayList<>(found);
    }

    // ---------- the check that makes this safe ----------

    /**
     * Looks the ASIN up on Amazon and keeps it only if it resolves.
     *
     * <p>This is the step that separates a suggestion from a guess. The title
     * and price returned are the marketplace's, so the user compares their
     * product against what Amazon actually holds rather than against a
     * description the model produced.</p>
     */
    private java.util.Optional<AsinSuggestion> verify(Product product, String asin) {
        try {
            var details = marketplaceService.getListing(Marketplace.AMAZON, asin);
            if (details == null || details.getTitle() == null) {
                return java.util.Optional.empty();
            }
            java.math.BigDecimal price = null;
            try {
                var snapshot = marketplaceService.getPrice(Marketplace.AMAZON, asin);
                price = snapshot == null ? null : snapshot.getItemPrice();
            } catch (RuntimeException e) {
                // A listing with no current offer is still the right product.
                log.debug("No price for suggested ASIN {}: {}", asin, e.getMessage());
            }
            // Within this product's own company. Checked across all companies,
            // it flagged an ASIN as "already used" because a different client
            // happened to sell the same item — and told this client so.
            boolean alreadyUsed = identifierRepo
                    .findByTenantIdAndTypeAndNormalizedValue(product.getTenantId(), IdentifierType.ASIN, asin)
                    .stream()
                    .anyMatch(i -> !i.getProduct().getId().equals(product.getId()));

            return java.util.Optional.of(AsinSuggestion.builder()
                    .asin(asin)
                    .title(details.getTitle())
                    .brand(details.getBrand())
                    .price(price)
                    .currency(details.getCurrency())
                    .url(details.getUrl())
                    .available(details.isAvailable())
                    .alreadyUsedByAnotherProduct(alreadyUsed)
                    .build());
        } catch (RuntimeException e) {
            // Does not exist in the marketplaces we can reach, so it is not an
            // answer — however confident the model was.
            log.info("Suggested ASIN {} did not resolve on Amazon: {}", asin, e.getMessage());
            return java.util.Optional.empty();
        }
    }
}
