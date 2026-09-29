package com.priceintel.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.priceintel.backend.entity.AiMatchVerdict;

@Repository
public interface AiMatchVerdictRepository extends JpaRepository<AiMatchVerdict, Long> {

    Optional<AiMatchVerdict> findByProductIdAndListingIdAndPromptVersion(
            Long productId, Long listingId, String promptVersion);

    /** Every current verdict for a product, for rendering the review queue in one query. */
    List<AiMatchVerdict> findByProductIdAndPromptVersion(Long productId, String promptVersion);

    List<AiMatchVerdict> findByListingIdIn(List<Long> listingIds);
}
