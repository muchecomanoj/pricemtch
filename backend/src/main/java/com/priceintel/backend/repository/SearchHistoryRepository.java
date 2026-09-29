package com.priceintel.backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.priceintel.backend.entity.SearchHistory;

@Repository
public interface SearchHistoryRepository extends JpaRepository<SearchHistory, Long> {

    @org.springframework.data.jpa.repository.Query("SELECT h FROM SearchHistory h WHERE (:tenantId IS NULL OR h.tenantId = :tenantId) ORDER BY h.createdAt DESC")
    Page<SearchHistory> findForList(@org.springframework.data.repository.query.Param("tenantId") Long tenantId, Pageable pageable);
}
