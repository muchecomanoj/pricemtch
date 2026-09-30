package com.priceintel.backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.priceintel.backend.entity.DemoRequest;

@Repository
public interface DemoRequestRepository extends JpaRepository<DemoRequest, Long> {

    Page<DemoRequest> findByHandled(boolean handled, Pageable pageable);

    long countByHandledFalse();
}
