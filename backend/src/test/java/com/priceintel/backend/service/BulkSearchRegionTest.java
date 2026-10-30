package com.priceintel.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.priceintel.backend.dto.request.ChannelSearchRequest;
import com.priceintel.backend.dto.response.ChannelSearchResponse;
import com.priceintel.backend.entity.BulkSearchJob;
import com.priceintel.backend.entity.BulkSearchRow;
import com.priceintel.backend.exception.BadRequestException;
import com.priceintel.backend.repository.BulkSearchJobRepository;
import com.priceintel.backend.repository.BulkSearchRowRepository;
import com.priceintel.backend.repository.BulkSearchRowSaveRepository;
import com.priceintel.backend.repository.ProductIdentifierRepository;
import com.priceintel.backend.repository.ProductRepository;
import com.priceintel.backend.security.TenantContext;
import com.priceintel.backend.service.ProductService;
import com.priceintel.backend.service.impl.AsyncBulkSearchProcessor;
import com.priceintel.backend.service.impl.BulkSearchService;
import com.priceintel.backend.service.impl.ChannelSearchPlanner;

/**
 * Which country's storefront a bulk search looks in.
 *
 * <p>A single search has always taken a region; a bulk upload had no way to say
 * one, so every row searched the authorised marketplaces and a file of UK
 * products came back empty — a result that reads as a fact about the products
 * rather than about where we looked.</p>
 *
 * <p>Two rules are worth protecting: the region reaches the search, and a code
 * that is not a country is refused rather than quietly replaced by the
 * default.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BulkSearchRegionTest {

    @Mock BulkSearchJobRepository jobRepo;
    @Mock BulkSearchRowRepository rowRepo;
    @Mock BulkSearchRowSaveRepository rowSaveRepo;
    @Mock ProductRepository productRepo;
    @Mock ProductIdentifierRepository identifierRepo;
    @Mock ProductService productService;
    @Mock AsyncBulkSearchProcessor processor;
    @Spy ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks BulkSearchService service;

    private static final long TENANT = 7L;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT, false);
        when(jobRepo.save(any())).thenAnswer(inv -> {
            BulkSearchJob job = inv.getArgument(0);
            if (job.getId() == null) {
                job.setId(1L);
            }
            return job;
        });
        when(rowRepo.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    private MockMultipartFile csv(String body) {
        return new MockMultipartFile("file", "competitor pricing.csv", "text/csv", body.getBytes());
    }

    /** Captures the rows the upload built, in file order. */
    private List<BulkSearchRow> rowsOf() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<BulkSearchRow>> captor = ArgumentCaptor.forClass(List.class);
        verify(rowRepo).saveAll(captor.capture());
        return captor.getValue();
    }

    @Nested
    @DisplayName("the region chosen at upload")
    class JobRegion {

        @Test
        @DisplayName("is recorded on the job, in the spelling the rest of the system uses")
        void regionIsStoredNormalised() {
            // The UI offers "UK"; everything that stores a storefront says GB.
            BulkSearchJob job = service.start(
                    csv("asin\nB0CTHMJC6Q\n"), true, null, "UK");

            assertThat(job.getRegion()).isEqualTo("GB");
        }

        @Test
        @DisplayName("left out keeps the old behaviour rather than assuming a country")
        void noRegionStaysNull() {
            BulkSearchJob job = service.start(csv("asin\nB07YXM6XTF\n"), true, null, null);

            // Not "US": a null region lets the planner search what it is
            // authorised for, which is what every job before this column did.
            assertThat(job.getRegion()).isNull();
        }

        @Test
        @DisplayName("a code that is not a country is refused, and nothing is queued")
        void unknownRegionIsRefused() {
            assertThatThrownBy(() -> service.start(csv("asin\nB07YXM6XTF\n"), true, null, "UKK"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Unknown region: UKK");

            // Defaulting to US would search the wrong catalogue for every row
            // and report the emptiness as the answer.
            verify(jobRepo, never()).save(any());
            verify(processor, never()).run(any());
        }
    }

    @Nested
    @DisplayName("a Region column in the file")
    class RowRegion {

        @Test
        @DisplayName("is read per row, under any of its accepted headings")
        void perRowRegionIsRead() {
            service.start(csv("""
                    asin,region
                    B0CTHMJC6Q,UK
                    B07YXM6XTF,US
                    B0DDHM6D3L,in
                    """), true, null, "US");

            assertThat(rowsOf()).extracting(BulkSearchRow::getInputRegion)
                    .containsExactly("GB", "US", "IN");
        }

        @Test
        @DisplayName("a blank cell leaves the row following the job")
        void blankCellMeansFollowTheJob() {
            service.start(csv("""
                    asin,storefront
                    B0CTHMJC6Q,GB
                    B07YXM6XTF,
                    """), true, null, "GB");

            assertThat(rowsOf()).extracting(BulkSearchRow::getInputRegion)
                    .containsExactly("GB", null);
        }

        @Test
        @DisplayName("a country-of-origin column is not mistaken for a storefront")
        void countryOfOriginIsNotARegion() {
            // The product import reads `country` as where the goods were made.
            // Searching amazon.cn because a kettle was manufactured in China
            // would be a different product entirely.
            service.start(csv("""
                    asin,country
                    B0CTHMJC6Q,CN
                    """), true, null, "GB");

            assertThat(rowsOf()).extracting(BulkSearchRow::getInputRegion)
                    .containsExactly((String) null);
        }

        @Test
        @DisplayName("a bad cell fails the upload naming the row the user has to fix")
        void badCellNamesItsRow() {
            assertThatThrownBy(() -> service.start(csv("""
                    asin,region
                    B0CTHMJC6Q,GB
                    B07YXM6XTF,Britain
                    """), true, null, "GB"))
                    .isInstanceOf(BadRequestException.class)
                    // +1 for the header, so the number matches the spreadsheet.
                    .hasMessageContaining("region in row 3")
                    .hasMessageContaining("Britain");
        }
    }

    /**
     * The region has to survive the hand-off to the background worker — the
     * tenant did not, once, and every search ran unscoped.
     */
    @Nested
    @DisplayName("what the search is actually asked for")
    class ReachesTheSearch {

        @Mock ChannelSearchPlanner planner;
        @Mock BulkSearchJobRepository processorJobRepo;
        @Mock BulkSearchRowRepository processorRowRepo;

        private AsyncBulkSearchProcessor worker;

        @BeforeEach
        void workerSetUp() {
            worker = new AsyncBulkSearchProcessor(
                    processorJobRepo, processorRowRepo, planner, new ObjectMapper());
            when(planner.search(any())).thenReturn(ChannelSearchResponse.builder()
                    .status("NONE_MATCHED").build());
        }

        private String regionSearchedFor(String jobRegion, String rowRegion) {
            BulkSearchJob job = BulkSearchJob.builder()
                    .tenantId(TENANT).region(jobRegion).judge(false).totalRows(1).build();
            job.setId(2L);
            BulkSearchRow row = BulkSearchRow.builder()
                    .jobId(2L).rowNumber(2).inputAsin("B0CTHMJC6Q").inputRegion(rowRegion)
                    .status(BulkSearchRow.Status.PENDING).build();
            row.setId(3L);

            when(processorJobRepo.findById(2L)).thenReturn(Optional.of(job));
            when(processorRowRepo.findByJobIdOrderByRowNumberAsc(2L)).thenReturn(List.of(row));

            worker.run(2L);

            ArgumentCaptor<ChannelSearchRequest> captor =
                    ArgumentCaptor.forClass(ChannelSearchRequest.class);
            verify(planner).search(captor.capture());
            return captor.getValue().getRegion();
        }

        @Test
        @DisplayName("the job's region, when the row names none")
        void jobRegionIsUsed() {
            assertThat(regionSearchedFor("GB", null)).isEqualTo("GB");
        }

        @Test
        @DisplayName("the row's own region wins, so one file can mix countries")
        void rowRegionWins() {
            assertThat(regionSearchedFor("US", "IN")).isEqualTo("IN");
        }

        @Test
        @DisplayName("no region at all is passed as none, not as a guess")
        void noRegionIsPassedThrough() {
            assertThat(regionSearchedFor(null, null)).isNull();
        }
    }
}
