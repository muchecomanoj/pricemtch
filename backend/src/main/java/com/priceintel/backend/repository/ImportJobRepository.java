package com.priceintel.backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.priceintel.backend.entity.ImportJob;

@Repository
public interface ImportJobRepository extends JpaRepository<ImportJob, Long> {

    @org.springframework.data.jpa.repository.Query("SELECT j FROM ImportJob j WHERE (:tenantId IS NULL OR j.tenantId = :tenantId) ORDER BY j.createdAt DESC")
    Page<ImportJob> findForList(@org.springframework.data.repository.query.Param("tenantId") Long tenantId, Pageable pageable);
}
