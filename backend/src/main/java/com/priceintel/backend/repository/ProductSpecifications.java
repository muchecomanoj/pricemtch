package com.priceintel.backend.repository;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import com.priceintel.backend.constants.ProductStatus;
import com.priceintel.backend.entity.Product;
import com.priceintel.backend.entity.ProductCategory;
import com.priceintel.backend.entity.ProductIdentifier;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;

/**
 * Composable query filters for {@link Product}. Any null/blank argument is
 * skipped, so the same method serves list, search, and filter.
 *
 * <p>{@code keyword} is the general search box: it spans SKU, title, brand,
 * description <b>and</b> identifiers, because users paste an ASIN into a search
 * box and expect the product back. {@code identifier} remains a separate exact
 * filter for callers that want to match on identifiers only.</p>
 */
public final class ProductSpecifications {

    private ProductSpecifications() {
    }

    public static Specification<Product> withFilters(String keyword, ProductStatus status,
                                                     String brand, String category, String identifier) {
        return withFilters(keyword, status, brand, category, identifier, null);
    }

    /**
     * @param hasCompetitors {@code true} keeps only products with at least one
     *                       non-rejected competitor listing <em>that has a
     *                       price</em>, {@code false} keeps only those without,
     *                       {@code null} does not filter.
     *                       Applied in the database because "select every product
     *                       with competitors" has to span the whole catalogue,
     *                       not just the page on screen.
     */
    public static Specification<Product> withFilters(String keyword, ProductStatus status,
                                                     String brand, String category, String identifier,
                                                     Boolean hasCompetitors) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (hasCompetitors != null) {
                if (query == null) {
                    throw new IllegalStateException(
                            "hasCompetitors needs a CriteriaQuery to build its subquery. "
                                    + "Failing loudly rather than returning every product "
                                    + "as though the filter had been applied.");
                }
                // EXISTS rather than a join: a join would multiply the product row
                // once per listing and need a DISTINCT to undo it, and the count
                // is irrelevant here — only whether there is at least one.
                jakarta.persistence.criteria.Subquery<Integer> sub = query.subquery(Integer.class);
                jakarta.persistence.criteria.Root<com.priceintel.backend.entity.CompetitorListing>
                        listing = sub.from(com.priceintel.backend.entity.CompetitorListing.class);
                // Priced, not merely present. The purpose of this filter is
                // "products a comparison can actually be made for", and a
                // listing with no price is not one — it would put products into
                // a bulk-alert selection whose rules could never fire.
                sub.select(cb.literal(1)).where(cb.and(
                        cb.equal(listing.get("productId"), root.get("id")),
                        cb.notEqual(listing.get("matchStatus"),
                                com.priceintel.backend.constants.MatchStatus.REJECTED),
                        cb.isNotNull(listing.get("lastPrice"))));
                predicates.add(hasCompetitors ? cb.exists(sub) : cb.not(cb.exists(sub)));
            }

            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim().toLowerCase() + "%";
                // Identifiers are matched too, so pasting an ASIN straight from
                // Amazon into the search box finds the product. Normalised the
                // same way identifiers are stored, so "b08l5np6ng" and
                // "B08-L5NP6NG" both hit.
                String idValue = keyword.trim().toUpperCase().replaceAll("[\\s-]", "");
                // LEFT join: a product with no identifiers must still be findable
                // by its title or SKU. An inner join would silently drop it.
                Join<Product, ProductIdentifier> keywordIdJoin =
                        root.join("identifiers", JoinType.LEFT);
                if (query != null) {
                    query.distinct(true); // the join can multiply rows
                }
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("sku")), like),
                        cb.like(cb.lower(root.get("title")), like),
                        cb.like(cb.lower(root.get("brand")), like),
                        cb.like(cb.lower(root.get("description")), like),
                        cb.equal(keywordIdJoin.get("normalizedValue"), idValue),
                        cb.like(cb.upper(keywordIdJoin.get("originalValue")), "%" + idValue + "%")));
            }

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            if (brand != null && !brand.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("brand")), brand.trim().toLowerCase()));
            }

            if (category != null && !category.isBlank()) {
                Join<Product, ProductCategory> categoryJoin = root.join("category");
                predicates.add(cb.equal(cb.lower(categoryJoin.get("name")), category.trim().toLowerCase()));
            }

            if (identifier != null && !identifier.isBlank()) {
                if (query != null) {
                    query.distinct(true);
                }
                String value = identifier.trim().toUpperCase().replaceAll("[\\s-]", "");
                Join<Product, ProductIdentifier> idJoin = root.join("identifiers");
                predicates.add(cb.or(
                        cb.equal(idJoin.get("normalizedValue"), value),
                        cb.like(cb.upper(idJoin.get("originalValue")), "%" + value + "%")));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
