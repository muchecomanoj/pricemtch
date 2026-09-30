package com.priceintel.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.priceintel.backend.constants.LandingSection;
import com.priceintel.backend.entity.LandingContent;

@Repository
public interface LandingContentRepository extends JpaRepository<LandingContent, Long> {

    Optional<LandingContent> findBySection(LandingSection section);

    List<LandingContent> findByActiveTrue();
}
