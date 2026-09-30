package com.priceintel.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.priceintel.backend.constants.LandingSection;
import com.priceintel.backend.dto.request.LandingContentUpdateRequest;
import com.priceintel.backend.entity.LandingContent;
import com.priceintel.backend.exception.BadRequestException;
import com.priceintel.backend.repository.LandingContentRepository;
import com.priceintel.backend.service.impl.LandingContentServiceImpl;

/**
 * Editable landing-page copy.
 *
 * <p>The page is the first thing a prospective customer sees, so the rule these
 * tests protect is that a bad edit costs one section, never the whole page.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LandingContentTest {

    @Mock LandingContentRepository repository;
    @Spy ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks LandingContentServiceImpl service;

    private static LandingContent section(LandingSection section, String payload, boolean active) {
        return LandingContent.builder().section(section).payload(payload).active(active).build();
    }

    @Test
    @DisplayName("the public payload is parsed JSON, not a string the browser must parse again")
    void payloadIsParsed() {
        when(repository.findByActiveTrue()).thenReturn(List.of(
                section(LandingSection.HERO, "{\"title\":\"Price with intelligence\"}", true)));

        Map<String, Object> content = service.publicContent();

        assertThat(content).containsKey("HERO");
        assertThat(content.get("HERO")).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) content.get("HERO")).get("title")).isEqualTo("Price with intelligence");
    }

    @Test
    @DisplayName("sections come back in page order, not in whatever order they were saved")
    void sectionsAreOrdered() {
        when(repository.findByActiveTrue()).thenReturn(List.of(
                section(LandingSection.CTA, "{}", true),
                section(LandingSection.HERO, "{}", true),
                section(LandingSection.FEATURES, "{}", true)));

        assertThat(service.publicContent().keySet())
                .containsExactly("HERO", "FEATURES", "CTA");
    }

    @Test
    @DisplayName("one section holding broken JSON is skipped — the rest of the page still renders")
    void oneBadSectionDoesNotBreakThePage() {
        when(repository.findByActiveTrue()).thenReturn(List.of(
                section(LandingSection.HERO, "{\"title\":\"Fine\"}", true),
                section(LandingSection.STATS, "{ this is not json", true)));

        Map<String, Object> content = service.publicContent();

        assertThat(content).containsOnlyKeys("HERO");
    }

    @Test
    @DisplayName("a hidden section is not served to visitors")
    void hiddenSectionIsNotPublic() {
        when(repository.findBySection(LandingSection.TESTIMONIALS))
                .thenReturn(Optional.of(section(LandingSection.TESTIMONIALS, "{\"items\":[]}", false)));

        assertThat(service.publicSection(LandingSection.TESTIMONIALS)).isNull();
    }

    @Test
    @DisplayName("a missing section returns null rather than failing the request")
    void missingSectionIsNull() {
        when(repository.findBySection(LandingSection.FAQS)).thenReturn(Optional.empty());

        assertThat(service.publicSection(LandingSection.FAQS)).isNull();
    }

    @Test
    @DisplayName("an edit that is not valid JSON is refused, and nothing is saved")
    void invalidJsonIsRefused() {
        assertThatThrownBy(() -> service.update(LandingSection.HERO,
                LandingContentUpdateRequest.builder().payload("{\"title\": ").build()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not valid JSON");

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("an edit that is valid JSON but not an object is refused")
    void nonObjectJsonIsRefused() {
        // "[1,2,3]" parses, but the page reads named fields off each section, so
        // an array would render as nothing with no error to explain why.
        assertThatThrownBy(() -> service.update(LandingSection.STATS,
                LandingContentUpdateRequest.builder().payload("[1,2,3]").build()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("JSON object");

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("a valid edit is saved, and leaves visibility alone when it is not given")
    void validEditIsSaved() {
        LandingContent existing = section(LandingSection.HERO, "{\"title\":\"Old\"}", true);
        when(repository.findBySection(LandingSection.HERO)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.update(LandingSection.HERO,
                LandingContentUpdateRequest.builder().payload("{\"title\":\"New\"}").build());

        assertThat(existing.getPayload()).isEqualTo("{\"title\":\"New\"}");
        assertThat(existing.isActive()).isTrue();
    }

    @Test
    @DisplayName("a section can be hidden without losing what was written in it")
    void sectionCanBeHidden() {
        LandingContent existing = section(LandingSection.TESTIMONIALS, "{\"items\":[{\"name\":\"A\"}]}", true);
        when(repository.findBySection(LandingSection.TESTIMONIALS)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.update(LandingSection.TESTIMONIALS, LandingContentUpdateRequest.builder()
                .payload(existing.getPayload()).active(false).build());

        assertThat(existing.isActive()).isFalse();
        assertThat(existing.getPayload()).contains("\"name\":\"A\"");
    }
}
