package com.priceintel.backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.priceintel.backend.entity.AiExecution;

@Repository
public interface AiExecutionRepository extends JpaRepository<AiExecution, Long> {

    @org.springframework.data.jpa.repository.Query("SELECT e FROM AiExecution e WHERE (:tenantId IS NULL OR e.tenantId = :tenantId) ORDER BY e.createdAt DESC")
    Page<AiExecution> findForList(@org.springframework.data.repository.query.Param("tenantId") Long tenantId, Pageable pageable);
}
