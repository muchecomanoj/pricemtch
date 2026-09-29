package com.priceintel.backend.controller;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.priceintel.backend.dto.request.Destination;
import com.priceintel.backend.dto.response.ApiResponse;
import com.priceintel.backend.dto.response.SaveRowResponse;
import com.priceintel.backend.entity.BulkSearchJob;
import com.priceintel.backend.entity.BulkSearchRow;
import com.priceintel.backend.service.impl.BulkSearchService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Bulk marketplace search from a CSV or XLSX upload (§13.1).
 *
 * <p>Searches only. Nothing found here enters the catalogue until a person opens
 * the results and saves a row — a bulk run over an unfamiliar list is exactly
 * where a wrong match would go unnoticed.</p>
 */
@RestController
@RequestMapping("/api/v1/search/bulk")
@RequiredArgsConstructor
@Tag(name = "Bulk Product Search",
        description = "Upload a file of products and search the marketplaces for all of them")
public class BulkSearchController {

    private static final String WRITE_ROLES = "hasAnyRole('ADMIN', 'MANAGER')";

    private final BulkSearchService service;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(WRITE_ROLES)
    @Operation(summary = "Upload a file and search every row on the marketplaces",
            description = "Runs in the background and returns immediately with a job id. Columns "
                    + "are detected from the headers using the same vocabulary as the product "
                    + "import — sku, title, brand, asin, upc, ean, gtin, mpn — so one file works "
                    + "for both.\n\n"
                    + "**This is slow, and that is not a fault.** Judging costs about 2,400 tokens "
                    + "against an 8,000/minute budget, so roughly three rows a minute: a 100-row "
                    + "file takes over half an hour. Rows run one at a time because firing them "
                    + "together would not finish sooner, only exhaust the shared quota faster. "
                    + "Poll the job for progress.\n\n"
                    + "Nothing is added to the catalogue. Save the rows you want afterwards.")
    public ResponseEntity<ApiResponse<BulkSearchJob>> start(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "true") boolean judge,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String postalCode) {
        Destination destination = (country == null && postalCode == null) ? null
                : Destination.builder().country(country).postalCode(postalCode).build();
        BulkSearchJob job = service.start(file, judge, destination);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(job,
                "Search accepted — " + job.getTotalRows() + " row(s) queued. Poll for progress."));
    }

    @GetMapping("/{jobId}")
    @Operation(summary = "Progress and counts for one bulk search",
            description = "`foundRows` matched something; `emptyRows` were searched and matched "
                    + "nothing, which is a result rather than a failure; `errorRows` could not be "
                    + "searched at all. The three are counted apart because they need different "
                    + "responses from the user.")
    public ResponseEntity<ApiResponse<BulkSearchJob>> status(@PathVariable Long jobId) {
        return ResponseEntity.ok(ApiResponse.success(service.get(jobId), "Bulk search status"));
    }

    @GetMapping("/{jobId}/rows")
    @Operation(summary = "The rows of a bulk search and what each one found",
            description = "`matchesJson` and `rejectedJson` hold the judged listings as the same "
                    + "shape the single-search endpoint returns. Filter with `status`: FOUND, "
                    + "NONE_MATCHED, NO_LISTINGS, NOT_JUDGED, ERROR or PENDING.")
    public ResponseEntity<ApiResponse<Page<BulkSearchRow>>> rows(
            @PathVariable Long jobId,
            @RequestParam(required = false) BulkSearchRow.Status status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                service.rows(jobId, status, page, size), "Bulk search rows"));
    }

    @PostMapping("/{jobId}/rows/{rowId}/save")
    @PreAuthorize(WRITE_ROLES)
    @Operation(summary = "Save one searched row into the catalogue, or update what is already there",
            description = "Creates the product with whatever identifiers the row carried, so a "
                    + "later competitor search resolves it exactly rather than by title.\n\n"
                    + "**Already in the catalogue is an answer, not an error.** When the product "
                    + "exists — recognised by identifier first, then SKU — nothing is written and "
                    + "the response comes back as `EXISTS` with a field-by-field `differences` "
                    + "list. Show it, ask the user, then call again with `update=true` to apply "
                    + "them. Calling with `update=false` never changes anything, so the dialog can "
                    + "always be dismissed safely.\n\n"
                    + "`status` is CREATED, EXISTS, UPDATED or UNCHANGED. A difference marked "
                    + "`advisory` is the marketplace price, which is what a seller charges on "
                    + "Amazon rather than what this catalogue should sell at — worth a second look "
                    + "before accepting.\n\n"
                    + "Pass `sku` to override the file's value, which may be blank or taken.")
    public ResponseEntity<ApiResponse<SaveRowResponse>> save(
            @PathVariable Long jobId,
            @PathVariable Long rowId,
            @RequestParam(required = false) String sku,
            @RequestParam(defaultValue = "false") boolean update) {
        SaveRowResponse result = service.saveToCatalogue(jobId, rowId, sku, update);
        return ResponseEntity.ok(ApiResponse.success(result, result.getMessage()));
    }

    @GetMapping
    @Operation(summary = "Bulk search history, newest first")
    public ResponseEntity<ApiResponse<Page<BulkSearchJob>>> history(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                service.history(page, size), "Bulk search history"));
    }
}
