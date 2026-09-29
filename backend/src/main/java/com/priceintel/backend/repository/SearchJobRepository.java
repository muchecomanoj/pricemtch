package com.priceintel.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.priceintel.backend.entity.SearchJob;

@Repository
public interface SearchJobRepository extends JpaRepository<SearchJob, Long> {
}
