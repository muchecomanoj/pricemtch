package com.priceintel.backend.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.priceintel.backend.constants.LandingSection;
import com.priceintel.backend.dto.request.LandingContentUpdateRequest;
import com.priceintel.backend.dto.response.ApiResponse;
import com.priceintel.backend.dto.response.LandingContentResponse;
import com.priceintel.backend.service.LandingContentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Super Admin editing of the public landing page's copy — the headline, the
 * stats band, the feature cards, testimonials and FAQs.
 *
 * <p>Prices are not here: those come from the subscription plans, so that the
 * page can never advertise a figure the signup flow does not charge.</p>
 */
@RestController
@RequestMapping("/api/v1/admin/landing-content")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "SUPER_ADMIN · Landing Content", description = "Edit the marketing copy on the public site")
public class LandingContentController {

    private final LandingContentService landingContentService;

    @GetMapping
    @Operation(summary = "List every section, including hidden ones")
    public ResponseEntity<ApiResponse<List<LandingContentResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success(landingContentService.listAll(), "Landing content"));
    }

    @PutMapping("/{section}")
    @Operation(summary = "Replace one section's content")
    public ResponseEntity<ApiResponse<LandingContentResponse>> update(
            @PathVariable LandingSection section,
            @Valid @RequestBody LandingContentUpdateRequest request) {

        return ResponseEntity.ok(ApiResponse.success(
                landingContentService.update(section, request), "Section saved"));
    }
}
