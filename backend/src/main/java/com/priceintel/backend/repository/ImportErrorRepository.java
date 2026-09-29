package com.priceintel.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.priceintel.backend.entity.ImportError;

@Repository
public interface ImportErrorRepository extends JpaRepository<ImportError, Long> {

    List<ImportError> findByImportJobIdOrderByRowNumberAsc(Long importJobId);

    long countByImportJobId(Long importJobId);
}
