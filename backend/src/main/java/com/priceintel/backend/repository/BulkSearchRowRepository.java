package com.priceintel.backend.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.priceintel.backend.entity.BulkSearchRow;

public interface BulkSearchRowRepository extends JpaRepository<BulkSearchRow, Long> {

    Page<BulkSearchRow> findByJobIdOrderByRowNumberAsc(Long jobId, Pageable pageable);

    List<BulkSearchRow> findByJobIdOrderByRowNumberAsc(Long jobId);

    Page<BulkSearchRow> findByJobIdAndStatusOrderByRowNumberAsc(
            Long jobId, BulkSearchRow.Status status, Pageable pageable);
}
