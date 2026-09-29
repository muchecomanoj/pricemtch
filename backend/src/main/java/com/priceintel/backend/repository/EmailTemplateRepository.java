package com.priceintel.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.priceintel.backend.constants.EmailTemplateKey;
import com.priceintel.backend.entity.EmailTemplate;

@Repository
public interface EmailTemplateRepository extends JpaRepository<EmailTemplate, Long> {

    Optional<EmailTemplate> findByTemplateKey(EmailTemplateKey templateKey);
}
