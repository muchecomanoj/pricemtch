package com.priceintel.backend.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.priceintel.backend.constants.IdentifierType;
import com.priceintel.backend.constants.ImportFieldCatalog;
import com.priceintel.backend.constants.ImportFileType;
import com.priceintel.backend.dto.request.CreateProductRequest;
import com.priceintel.backend.dto.request.Destination;
import com.priceintel.backend.dto.request.ProductIdentifierRequest;
import com.priceintel.backend.dto.request.UpdateProductRequest;
import com.priceintel.backend.dto.response.SaveRowResponse;
import com.priceintel.backend.entity.BulkSearchJob;
import com.priceintel.backend.entity.BulkSearchRow;
import com.priceintel.backend.entity.Product;
import com.priceintel.backend.entity.ProductIdentifier;
import com.priceintel.backend.exception.BadRequestException;
import com.priceintel.backend.exception.ResourceNotFoundException;
import com.priceintel.backend.repository.BulkSearchJobRepository;
import com.priceintel.backend.repository.BulkSearchRowRepository;
import com.priceintel.backend.repository.ProductIdentifierRepository;
import com.priceintel.backend.repository.ProductRepository;
import com.priceintel.backend.utils.IdentifierNormalizer;
import com.priceintel.backend.security.TenantContext;
import com.priceintel.backend.service.ProductService;
import com.priceintel.backend.utils.SpreadsheetParser;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Bulk marketplace search from an uploaded file (§13.1).
 *
 * <p>Reads the same column vocabulary as the product import, so one file works
 * for both. What it does with the file is the opposite: the import creates
 * products and never calls a marketplace; this calls marketplaces and creates
 * nothing until a person asks it to.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BulkSearchService {

    private final BulkSearchJobRepository jobRepo;
    private final BulkSearchRowRepository rowRepo;
    private final AsyncBulkSearchProcessor processor;
    private final ProductService productService;
    private final ProductRepository productRepo;
    private final ProductIdentifierRepository identifierRepo;
    private final ObjectMapper objectMapper;

    /**
     * A ceiling on rows per file.
     *
     * <p>Not arbitrary: at roughly three rows a minute, 500 rows is nearly three
     * hours. Beyond that a single upload monopolises the shared AI and
     * marketplace quota for every other client, and the person who uploaded it
     * has no way to tell whether it is working or wedged.</p>
     */
    private static final int MAX_ROWS = 500;

    // ---------- starting a job ----------

    @Transactional
    public BulkSearchJob start(MultipartFile file, boolean judge, Destination destination) {
        ImportFileType type = detectFileType(file);
        List<Map<String, String>> rows;
        Map<String, String> mapping;
        try {
            com.priceintel.backend.utils.ParsedSheet sheet =
                    SpreadsheetParser.parse(file.getBytes(), type);
            rows = sheet.getRows();
            mapping = autoMap(sheet.getHeaders());
        } catch (Exception e) {
            throw new BadRequestException("Could not read that file: " + e.getMessage());
        }

        if (rows.isEmpty()) {
            throw new BadRequestException("That file has no data rows.");
        }
        if (rows.size() > MAX_ROWS) {
            throw new BadRequestException("That file has " + rows.size() + " rows. The limit is "
                    + MAX_ROWS + " — a larger run would hold the shared marketplace and AI quota "
                    + "for hours. Split the file and upload it in parts.");
        }
        if (!mapping.containsKey("title") && !hasAnyIdentifierColumn(mapping)) {
            throw new BadRequestException("Nothing to search for. The file needs a title column, "
                    + "or at least one of: asin, upc, ean, gtin, mpn.");
        }

        BulkSearchJob job = jobRepo.save(BulkSearchJob.builder()
                .tenantId(TenantContext.getTenantId())
                .fileName(file.getOriginalFilename())
                .fileType(type)
                .status(BulkSearchJob.Status.PENDING)
                .judge(judge)
                .destinationCountry(destination == null ? null : destination.normalisedCountry())
                .destinationPostalCode(destination == null ? null : destination.normalisedPostalCode())
                .totalRows(rows.size())
                .build());

        List<BulkSearchRow> toSave = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> row = rows.get(i);
            toSave.add(BulkSearchRow.builder()
                    .jobId(job.getId())
                    // +1 for zero-index, +1 for the header, so the number shown
                    // matches what the user sees in their spreadsheet.
                    .rowNumber(i + 2)
                    .inputSku(value(row, mapping, "sku"))
                    .inputTitle(value(row, mapping, "title"))
                    .inputBrand(value(row, mapping, "brand"))
                    .inputAsin(value(row, mapping, "asin"))
                    .inputUpc(value(row, mapping, "upc"))
                    .inputEan(value(row, mapping, "ean"))
                    .inputGtin(value(row, mapping, "gtin"))
                    .inputMpn(value(row, mapping, "mpn"))
                    .status(BulkSearchRow.Status.PENDING)
                    .build());
        }
        rowRepo.saveAll(toSave);

        log.info("Queued bulk search job {} — {} row(s), judge={}", job.getId(), rows.size(), judge);
        startAfterCommit(job.getId());
        return job;
    }

    /**
     * Hands the job to the background worker once the rows are actually visible.
     *
     * <p>Starting it inline loses a race: this method is transactional, so the
     * job and its rows are still uncommitted when it returns, and a worker on
     * another thread reads a database that does not yet contain them. It is
     * timing-dependent and therefore intermittent — two runs succeeded and the
     * third failed four milliseconds after being queued.</p>
     */
    private void startAfterCommit(Long jobId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            processor.run(jobId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                processor.run(jobId);
            }
        });
    }

    // ---------- reading ----------

    @Transactional(readOnly = true)
    public BulkSearchJob get(Long jobId) {
        BulkSearchJob job = jobRepo.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Bulk search job not found: " + jobId));
        Long tenant = TenantContext.getTenantId();
        if (tenant != null && job.getTenantId() != null && !tenant.equals(job.getTenantId())) {
            // Reported as missing rather than forbidden: whether another
            // client's job exists is itself information.
            throw new ResourceNotFoundException("Bulk search job not found: " + jobId);
        }
        return job;
    }

    @Transactional(readOnly = true)
    public Page<BulkSearchRow> rows(Long jobId, BulkSearchRow.Status status, int page, int size) {
        get(jobId);
        Pageable pageable = PageRequest.of(page, size);
        return status == null
                ? rowRepo.findByJobIdOrderByRowNumberAsc(jobId, pageable)
                : rowRepo.findByJobIdAndStatusOrderByRowNumberAsc(jobId, status, pageable);
    }

    @Transactional(readOnly = true)
    public Page<BulkSearchJob> history(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Long tenant = TenantContext.getTenantId();
        return tenant == null
                ? jobRepo.findAllByOrderByIdDesc(pageable)
                : jobRepo.findByTenantIdOrderByIdDesc(tenant, pageable);
    }

    // ---------- saving a result into the catalogue ----------

    /**
     * Creates a product from one searched row.
     *
     * <p>Explicitly a separate step from the search. A bulk run over an
     * unfamiliar list is exactly where a wrong match is least likely to be
     * noticed, so nothing enters the catalogue until someone has looked at the
     * result and asked for it.</p>
     *
     * @param sku overrides the file's SKU, which may be blank or already taken
     */
    @Transactional
    public SaveRowResponse saveToCatalogue(Long jobId, Long rowId, String sku, boolean update) {
        get(jobId);
        BulkSearchRow row = rowRepo.findById(rowId)
                .orElseThrow(() -> new ResourceNotFoundException("Row not found: " + rowId));
        if (!jobId.equals(row.getJobId())) {
            throw new BadRequestException("That row belongs to a different job.");
        }

        // Already saved by this row: show what it produced rather than making
        // a second copy of the same thing.
        if (row.getSavedProductId() != null) {
            Product existing = productRepo.findById(row.getSavedProductId()).orElse(null);
            if (existing != null) {
                return compareOrUpdate(row, existing, "this row", update);
            }
        }

        // Already in the catalogue from somewhere else — an earlier upload, a
        // manual entry, another job. Recognised by identifier first, because a
        // barcode is a fact about the product while a SKU is a private label.
        Match found = findExisting(row, sku);
        if (found != null) {
            return compareOrUpdate(row, found.product(), found.matchedOn(), update);
        }

        String finalSku = firstNonBlank(sku, row.getInputSku(), row.getInputAsin(),
                row.getInputUpc(), row.getInputEan());
        if (finalSku == null) {
            throw new BadRequestException(
                    "This row has no SKU and no identifier to use as one. Provide a SKU to save it.");
        }
        String title = firstNonBlank(row.getInputTitle(), row.getResolvedQuery(), finalSku);

        var created = productService.createProduct(CreateProductRequest.builder()
                .sku(finalSku.trim())
                .title(title.trim())
                .brand(row.getInputBrand())
                .identifiers(identifiersOf(row))
                .build());

        row.setSavedProductId(created.getId());
        rowRepo.save(row);
        log.info("Bulk search row {} saved as product {}", rowId, created.getId());
        return SaveRowResponse.builder()
                .status("CREATED")
                .productId(created.getId())
                .productSku(created.getSku())
                .productTitle(created.getTitle())
                .message("Added to your catalogue.")
                .differences(List.of())
                .build();
    }

    /** An existing product and how we recognised it. */
    private record Match(Product product, String matchedOn) {
    }

    /**
     * Finds the catalogue product this row already refers to.
     *
     * <p>Identifiers are checked before the SKU: a barcode identifies the
     * product itself, while a SKU is only the label one seller put on it. Two
     * rows with different SKUs and the same ASIN are the same product.</p>
     */
    private Match findExisting(BulkSearchRow row, String sku) {
        Map<IdentifierType, String> candidates = new LinkedHashMap<>();
        putId(candidates, IdentifierType.ASIN, row.getInputAsin());
        putId(candidates, IdentifierType.UPC, row.getInputUpc());
        putId(candidates, IdentifierType.EAN, row.getInputEan());
        putId(candidates, IdentifierType.GTIN, row.getInputGtin());
        putId(candidates, IdentifierType.MPN, row.getInputMpn());

        for (var entry : candidates.entrySet()) {
            String normalised = IdentifierNormalizer.normalize(entry.getKey(), entry.getValue());
            for (ProductIdentifier pi : identifierRepo
                    .findByTypeAndNormalizedValue(entry.getKey(), normalised)) {
                Product p = pi.getProduct();
                if (p != null && inTenant(p)) {
                    return new Match(p, entry.getKey() + " " + entry.getValue());
                }
            }
        }

        String candidateSku = firstNonBlank(sku, row.getInputSku());
        if (candidateSku != null) {
            // Looked up within the company. Platform-wide, a SKU two companies
            // both use would match two rows and the lookup would throw.
            Long tenant = TenantContext.getTenantId();
            var bySku = tenant == null
                    ? productRepo.findBySkuIgnoreCase(candidateSku.trim()).map(java.util.List::of)
                            .orElseGet(java.util.List::of)
                    : productRepo.findByTenantIdAndSkuIgnoreCase(tenant, candidateSku.trim());
            if (!bySku.isEmpty() && inTenant(bySku.get(0))) {
                return new Match(bySku.get(0), "SKU " + candidateSku.trim());
            }
        }
        return null;
    }

    /**
     * Reports how the fetched details differ from the stored ones, and applies
     * them only when asked.
     *
     * <p>Never updates without {@code update=true}. The first call answers "what
     * would change", the second performs it — so the confirmation dialog shows
     * the user what a "yes" actually does rather than asking them to approve
     * something they cannot see.</p>
     */
    private SaveRowResponse compareOrUpdate(BulkSearchRow row, Product product,
                                            String matchedOn, boolean update) {
        List<SaveRowResponse.FieldDifference> diffs = new ArrayList<>();

        String incomingTitle = firstNonBlank(row.getInputTitle(), row.getResolvedQuery());
        addDiff(diffs, "title", product.getTitle(), incomingTitle, false);
        addDiff(diffs, "brand", product.getBrand(), row.getInputBrand(), false);

        // The marketplace price is what a seller charges on Amazon, not
        // necessarily what this catalogue should sell at — flagged advisory so
        // the dialog can mark it apart from a plain title correction.
        BigDecimal marketPrice = topMatchPrice(row);
        addDiff(diffs, "ourPrice",
                product.getOurPrice() == null ? null : product.getOurPrice().toPlainString(),
                marketPrice == null ? null : marketPrice.toPlainString(), true);

        // Identifiers the row carries that differ from the product's — both the
        // ones it lacks entirely and the ones it holds a different value for.
        // An identifier type the row says nothing about is never touched.
        Map<IdentifierType, String> existing = new LinkedHashMap<>();
        identifierRepo.findByProductId(product.getId())
                .forEach(pi -> existing.put(pi.getType(), pi.getNormalizedValue()));
        for (ProductIdentifierRequest req : identifiersOfList(row)) {
            String normalised = IdentifierNormalizer.normalize(req.getType(), req.getOriginalValue());
            if (!normalised.equals(existing.get(req.getType()))) {
                addDiff(diffs, "identifier." + req.getType(),
                        existing.get(req.getType()), req.getOriginalValue(), false);
            }
        }

        if (diffs.isEmpty()) {
            return response(row, product, matchedOn, "UNCHANGED",
                    "Already in your catalogue, and nothing has changed.", List.of());
        }
        if (!update) {
            return response(row, product, matchedOn, "EXISTS",
                    "\"" + product.getTitle() + "\" is already in your catalogue (SKU "
                            + product.getSku() + "). " + diffs.size()
                            + " detail(s) differ. Update it?", diffs);
        }

        UpdateProductRequest.UpdateProductRequestBuilder req = UpdateProductRequest.builder();
        for (SaveRowResponse.FieldDifference d : diffs) {
            switch (d.getField()) {
                case "title" -> req.title(d.getIncoming());
                case "brand" -> req.brand(d.getIncoming());
                case "ourPrice" -> req.ourPrice(
                        d.getIncoming() == null ? null : new BigDecimal(d.getIncoming()));
                default -> { /* identifiers are applied below, as a set */ }
            }
        }
        // Identifiers are replaced as a whole list, so send the merged set: the
        // incoming ones win for their own type, and every other type the product
        // already holds is carried over. Sending only the incoming ones would
        // silently drop the rest.
        //
        // Incoming wins deliberately. The difference list showed the user
        // "MPN: DUO60V5 -> IP-DUO60" and they approved it; keeping the old value
        // would make the confirmation dialog a lie about what "yes" does.
        Map<IdentifierType, ProductIdentifierRequest> merged = new LinkedHashMap<>();
        identifierRepo.findByProductId(product.getId()).forEach(pi -> merged.put(pi.getType(),
                ProductIdentifierRequest.builder()
                        .type(pi.getType()).originalValue(pi.getOriginalValue()).build()));
        identifiersOfList(row).forEach(incoming -> merged.put(incoming.getType(), incoming));
        req.identifiers(new ArrayList<>(merged.values()));

        var updated = productService.updateProduct(product.getId(), req.build());
        row.setSavedProductId(updated.getId());
        rowRepo.save(row);
        log.info("Bulk search row {} updated product {} ({} field(s))",
                row.getId(), updated.getId(), diffs.size());

        return response(row, product, matchedOn, "UPDATED",
                "Updated " + diffs.size() + " detail(s) on \"" + updated.getTitle() + "\".", diffs);
    }

    private SaveRowResponse response(BulkSearchRow row, Product product, String matchedOn,
            String status, String message, List<SaveRowResponse.FieldDifference> diffs) {
        return SaveRowResponse.builder()
                .status(status)
                .productId(product.getId())
                .productSku(product.getSku())
                .productTitle(product.getTitle())
                .matchedOn(matchedOn)
                .message(message)
                .differences(diffs)
                .build();
    }

    /** The price of the best listing this row matched, or null when it matched none. */
    private BigDecimal topMatchPrice(BulkSearchRow row) {
        if (row.getMatchesJson() == null || row.getMatchesJson().isBlank()) {
            return null;
        }
        try {
            var node = objectMapper.readTree(row.getMatchesJson());
            if (node.isArray() && !node.isEmpty() && node.get(0).path("price").isNumber()) {
                return node.get(0).get("price").decimalValue();
            }
        } catch (Exception e) {
            log.debug("Could not read match price for row {}: {}", row.getId(), e.getMessage());
        }
        return null;
    }

    private void addDiff(List<SaveRowResponse.FieldDifference> diffs, String field,
            String current, String incoming, boolean advisory) {
        // Absent incoming data is not a reason to blank a stored value: the file
        // simply did not mention it.
        if (incoming == null || incoming.isBlank() || incoming.equals(current)) {
            return;
        }
        diffs.add(SaveRowResponse.FieldDifference.builder()
                .field(field).current(current).incoming(incoming).advisory(advisory).build());
    }

    private boolean inTenant(Product p) {
        Long tenant = TenantContext.getTenantId();
        return tenant == null || p.getTenantId() == null || tenant.equals(p.getTenantId());
    }

    private void putId(Map<IdentifierType, String> map, IdentifierType type, String value) {
        if (value != null && !value.isBlank()) {
            map.put(type, value.trim());
        }
    }

    private List<ProductIdentifierRequest> identifiersOfList(BulkSearchRow row) {
        List<ProductIdentifierRequest> list = new ArrayList<>();
        addIdentifier(list, IdentifierType.ASIN, row.getInputAsin());
        addIdentifier(list, IdentifierType.UPC, row.getInputUpc());
        addIdentifier(list, IdentifierType.EAN, row.getInputEan());
        addIdentifier(list, IdentifierType.GTIN, row.getInputGtin());
        addIdentifier(list, IdentifierType.MPN, row.getInputMpn());
        return list;
    }

    private List<ProductIdentifierRequest> identifiersOf(BulkSearchRow row) {
        List<ProductIdentifierRequest> list = identifiersOfList(row);
        return list.isEmpty() ? null : list;
    }

    // ---------- helpers ----------

    /** Maps our field names onto the file's headers, using the import aliases. */
    private Map<String, String> autoMap(List<String> headers) {
        Map<String, String> mapping = new LinkedHashMap<>();
        for (var entry : ImportFieldCatalog.FIELD_ALIASES.entrySet()) {
            for (String header : headers) {
                if (entry.getValue().contains(ImportFieldCatalog.normalizeKey(header))) {
                    mapping.putIfAbsent(entry.getKey(), header);
                }
            }
        }
        return mapping;
    }

    private boolean hasAnyIdentifierColumn(Map<String, String> mapping) {
        return mapping.containsKey("asin") || mapping.containsKey("upc")
                || mapping.containsKey("ean") || mapping.containsKey("gtin")
                || mapping.containsKey("mpn");
    }

    private String value(Map<String, String> row, Map<String, String> mapping, String field) {
        String header = mapping.get(field);
        if (header == null) {
            return null;
        }
        String v = row.get(header);
        return v == null || v.isBlank() ? null : v.trim();
    }

    private void addIdentifier(List<ProductIdentifierRequest> list,
            com.priceintel.backend.constants.IdentifierType type, String value) {
        if (value != null && !value.isBlank()) {
            list.add(ProductIdentifierRequest.builder().type(type).originalValue(value.trim()).build());
        }
    }

    private String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private ImportFileType detectFileType(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("No file was uploaded, or the file is empty.");
        }
        String name = file.getOriginalFilename();
        if (name != null) {
            String lower = name.toLowerCase();
            if (lower.endsWith(".csv")) {
                return ImportFileType.CSV;
            }
            if (lower.endsWith(".xlsx")) {
                return ImportFileType.XLSX;
            }
        }
        throw new BadRequestException("Unsupported file type. Please upload a .csv or .xlsx file.");
    }
}
