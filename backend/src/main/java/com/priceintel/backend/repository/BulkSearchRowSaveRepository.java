package com.priceintel.backend.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.priceintel.backend.entity.BulkSearchRowSave;

@Repository
public interface BulkSearchRowSaveRepository extends JpaRepository<BulkSearchRowSave, Long> {

    Optional<BulkSearchRowSave> findByRowIdAndMarketplaceIgnoreCaseAndMarketplaceItemId(
            Long rowId, String marketplace, String marketplaceItemId);

    List<BulkSearchRowSave> findByRowId(Long rowId);

    /** One query for a whole page of rows, rather than one per row. */
    List<BulkSearchRowSave> findByRowIdIn(Collection<Long> rowIds);
}
