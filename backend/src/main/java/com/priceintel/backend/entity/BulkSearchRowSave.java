package com.priceintel.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One matched listing that has been saved into the catalogue from a bulk-search
 * row.
 *
 * <p>A row matches several listings and each can be saved separately, so a row
 * produces several products. {@code BulkSearchRow.savedProductId} holds a single
 * id and cannot express that; it is still written for the row-level save so old
 * behaviour is unchanged, and this records the rest.</p>
 */
@Entity
@Table(name = "bulk_search_row_saves",
        uniqueConstraints = @UniqueConstraint(name = "uk_row_save_listing",
                columnNames = {"row_id", "marketplace", "marketplace_item_id"}),
        indexes = @Index(name = "idx_row_save_row", columnList = "row_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BulkSearchRowSave extends BaseEntity {

    @Column(name = "row_id", nullable = false)
    private Long rowId;

    @Column(nullable = false, length = 30)
    private String marketplace;

    /** The listing's own id — an ASIN on Amazon, an item id on eBay. */
    @Column(name = "marketplace_item_id", nullable = false, length = 100)
    private String marketplaceItemId;

    @Column(name = "product_id", nullable = false)
    private Long productId;
}
