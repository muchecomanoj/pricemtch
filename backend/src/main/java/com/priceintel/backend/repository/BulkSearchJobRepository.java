package com.priceintel.backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.priceintel.backend.entity.BulkSearchJob;

public interface BulkSearchJobRepository extends JpaRepository<BulkSearchJob, Long> {

    Page<BulkSearchJob> findByTenantIdOrderByIdDesc(Long tenantId, Pageable pageable);

    Page<BulkSearchJob> findAllByOrderByIdDesc(Pageable pageable);
}
