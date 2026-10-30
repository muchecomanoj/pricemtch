package com.priceintel.backend.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.priceintel.backend.constants.IdentifierType;
import com.priceintel.backend.constants.ImportFieldCatalog;
import com.priceintel.backend.constants.ImportFileType;
import com.priceintel.backend.dto.request.CreateProductRequest;
import com.priceintel.backend.dto.request.Destination;
import com.priceintel.backend.dto.request.ProductIdentifierRequest;
import com.priceintel.backend.dto.request.UpdateProductRequest;
import com.priceintel.backend.dto.response.SaveRowResponse;
import com.priceintel.backend.dto.response.SavedListing;
import com.priceintel.backend.entity.BulkSearchJob;
import com.priceintel.backend.entity.BulkSearchRow;
import com.priceintel.backend.entity.BulkSearchRowSave;
import com.priceintel.backend.entity.Product;
import com.priceintel.backend.entity.ProductIdentifier;
import com.priceintel.backend.exception.BadRequestException;
import com.priceintel.backend.exception.ResourceNotFoundException;
import com.priceintel.backend.repository.BulkSearchJobRepository;
import com.priceintel.backend.repository.BulkSearchRowRepository;
import com.priceintel.backend.repository.BulkSearchRowSaveRepository;
import com.priceintel.backend.repository.ProductIdentifierRepository;
import com.priceintel.backend.repository.ProductRepository;
import com.priceintel.backend.utils.IdentifierNormalizer;
import com.priceintel.backend.security.TenantContext;
import com.priceintel.backend.service.ProductService;
import com.priceintel.backend.utils.SpreadsheetParser;
import com.priceintel.backend.utils.Storefront;

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
    private final BulkSearchRowSaveRepository rowSaveRepo;
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

    /**
     * Header aliases for the per-row Region column.
     *
     * <p>Kept here rather than in {@link ImportFieldCatalog} because that
     * catalogue is shared with the product import, where {@code country} already
     * means country of origin. A column saying where a product was made must not
     * decide which storefront is searched.</p>
     */
    private static final Set<String> REGION_ALIASES = Set.of(
            "region", "searchregion", "storefront", "marketplaceregion", "countrycode");

    @Transactional
    public BulkSearchJob start(MultipartFile file, boolean judge, Destination destination) {
        return start(file, judge, destination, null);
    }

    @Transactional
    public BulkSearchJob start(MultipartFile file, boolean judge, Destination destination,
            String region) {
        ImportFileType type = detectFileType(file);
        List<Map<String, String>> rows;
        Map<String, String> mapping;
        List<String> headers;
        try {
            com.priceintel.backend.utils.ParsedSheet sheet =
                    SpreadsheetParser.parse(file.getBytes(), type);
            rows = sheet.getRows();
            headers = sheet.getHeaders();
            mapping = autoMap(headers);
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

        String jobRegion = normalisedRegion(region, "region");
        String regionHeader = regionHeaderIn(headers);

        BulkSearchJob job = jobRepo.save(BulkSearchJob.builder()
                .tenantId(TenantContext.getTenantId())
                .fileName(file.getOriginalFilename())
                .fileType(type)
                .status(BulkSearchJob.Status.PENDING)
                .judge(judge)
                .region(jobRegion)
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
                    // A bad code here fails the upload rather than being
                    // ignored: a row quietly searched in the wrong country comes
                    // back "not found", which reads as a fact about the product.
                    .inputRegion(regionHeader == null ? null
                            : normalisedRegion(row.get(regionHeader), "region in row " + (i + 2)))
                    .status(BulkSearchRow.Status.PENDING)
                    .build());
        }
        rowRepo.saveAll(toSave);

        log.info("Queued bulk search job {} — {} row(s), judge={}, region={}",
                job.getId(), rows.size(), judge, jobRegion == null ? "any" : jobRegion);
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
        Page<BulkSearchRow> rows = status == null
                ? rowRepo.findByJobIdOrderByRowNumberAsc(jobId, pageable)
                : rowRepo.findByJobIdAndStatusOrderByRowNumberAsc(jobId, status, pageable);
        attachSavedListings(rows.getContent());
        return rows;
    }

    /**
     * Marks up each row with the listings it has already produced a product for.
     *
     * <p>Without this the "Added as …" badge lives only in the browser and
     * vanishes on reload, leaving the user unable to tell which of a dozen cards
     * they already saved — and clicking again is the natural way to find out.</p>
     */
    private void attachSavedListings(List<BulkSearchRow> rows) {
        if (rows.isEmpty()) {
            return;
        }
        List<Long> ids = rows.stream().map(BulkSearchRow::getId).toList();
        // One query for the page. Asking per row turns a 25-row page into 26
        // round trips for a badge.
        Map<Long, List<BulkSearchRowSave>> byRow = rowSaveRepo.findByRowIdIn(ids).stream()
                .collect(Collectors.groupingBy(BulkSearchRowSave::getRowId));
        if (byRow.isEmpty()) {
            rows.forEach(row -> row.setSavedListings(List.of()));
            return;
        }

        Map<Long, Product> products = productRepo.findAllById(
                        byRow.values().stream().flatMap(List::stream)
                                .map(BulkSearchRowSave::getProductId).distinct().toList())
                .stream().collect(Collectors.toMap(Product::getId, p -> p));

        for (BulkSearchRow row : rows) {
            row.setSavedListings(byRow.getOrDefault(row.getId(), List.of()).stream()
                    .map(save -> SavedListing.builder()
                            .marketplace(save.getMarketplace())
                            .marketplaceItemId(save.getMarketplaceItemId())
                            .productId(save.getProductId())
                            .productSku(products.containsKey(save.getProductId())
                                    ? products.get(save.getProductId()).getSku() : null)
                            .build())
                    .toList());
        }
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
        return saveToCatalogue(jobId, rowId, sku, update, null, null);
    }

    /**
     * Saves one of the row's matched listings, or the row itself.
     *
     * <p>Naming a listing saves that listing: its title, its identifiers and its
     * price, rather than what the spreadsheet said. A row matches several
     * listings and each can be saved separately, so one row can produce several
     * products — which is why the "already saved by this row" shortcut is
     * skipped here, and why a separate table records which listings a row has
     * produced.</p>
     *
     * <p>Omitting both parameters is the original behaviour, unchanged.</p>
     */
    @Transactional
    public SaveRowResponse saveToCatalogue(Long jobId, Long rowId, String sku, boolean update,
            String marketplace, String marketplaceItemId) {
        get(jobId);
        BulkSearchRow row = rowRepo.findById(rowId)
                .orElseThrow(() -> new ResourceNotFoundException("Row not found: " + rowId));
        if (!jobId.equals(row.getJobId())) {
            throw new BadRequestException("That row belongs to a different job.");
        }

        boolean listingNamed = marketplace != null && !marketplace.isBlank()
                && marketplaceItemId != null && !marketplaceItemId.isBlank();
        SaveSource source = listingNamed
                ? listingSource(row, marketplace, marketplaceItemId)
                : rowSource(row);

        if (listingNamed) {
            // This exact card, saved before. Returning what it produced is the
            // difference between a double-click being harmless and it making a
            // second copy of the same product.
            var alreadySaved = rowSaveRepo
                    .findByRowIdAndMarketplaceIgnoreCaseAndMarketplaceItemId(
                            rowId, source.marketplace(), source.marketplaceItemId());
            if (alreadySaved.isPresent()) {
                Product existing = productRepo.findById(alreadySaved.get().getProductId()).orElse(null);
                if (existing != null) {
                    return compareOrUpdate(row, source, existing, "this listing", update);
                }
            }
        } else if (row.getSavedProductId() != null) {
            // Already saved by this row: show what it produced rather than making
            // a second copy of the same thing. Deliberately not applied when a
            // listing is named, or every card after the first would answer with
            // the first card's product.
            Product existing = productRepo.findById(row.getSavedProductId()).orElse(null);
            if (existing != null) {
                return compareOrUpdate(row, source, existing, "this row", update);
            }
        }

        // Already in the catalogue from somewhere else — an earlier upload, a
        // manual entry, another job. Recognised by identifier first, because a
        // barcode is a fact about the product while a SKU is a private label.
        Match found = findExisting(source, sku);
        if (found != null) {
            SaveRowResponse response = compareOrUpdate(row, source, found.product(),
                    found.matchedOn(), update);
            // Remember the link even when the product already existed, so the
            // card shows as saved and a second click does not re-ask.
            if (listingNamed) {
                recordSave(row, source, found.product().getId());
            }
            return response;
        }

        String finalSku = firstNonBlank(source.skuCandidates().toArray(String[]::new));
        finalSku = firstNonBlank(sku, finalSku);
        if (finalSku == null) {
            throw new BadRequestException(
                    "This row has no SKU and no identifier to use as one. Provide a SKU to save it.");
        }
        String title = firstNonBlank(source.title(), finalSku);

        var created = productService.createProduct(CreateProductRequest.builder()
                .sku(finalSku.trim())
                .title(title.trim())
                .brand(source.brand())
                .identifiers(source.identifierRequests())
                .build());

        if (listingNamed) {
            recordSave(row, source, created.getId());
            log.info("Bulk search row {} saved listing {}/{} as product {}",
                    rowId, source.marketplace(), source.marketplaceItemId(), created.getId());
        } else {
            row.setSavedProductId(created.getId());
            rowRepo.save(row);
            log.info("Bulk search row {} saved as product {}", rowId, created.getId());
        }

        return SaveRowResponse.builder()
                .status("CREATED")
                .productId(created.getId())
                .productSku(created.getSku())
                .productTitle(created.getTitle())
                .marketplace(source.marketplace())
                .marketplaceItemId(source.marketplaceItemId())
                .message("Added to your catalogue.")
                .differences(List.of())
                .build();
    }

    private void recordSave(BulkSearchRow row, SaveSource source, Long productId) {
        rowSaveRepo.save(BulkSearchRowSave.builder()
                .rowId(row.getId())
                .marketplace(source.marketplace())
                .marketplaceItemId(source.marketplaceItemId())
                .productId(productId)
                .build());
        // Kept in step so a row that has produced anything still reports one
        // product to callers that only read this field.
        if (row.getSavedProductId() == null) {
            row.setSavedProductId(productId);
            rowRepo.save(row);
        }
    }

    /** An existing product and how we recognised it. */
    private record Match(Product product, String matchedOn) {
    }

    /**
     * What a save reads its values from — the spreadsheet row, or one listing
     * the row matched.
     *
     * <p>The two differ in every field that matters, so collecting them once
     * keeps the lookup, comparison and creation paths identical rather than
     * dotted with "if a listing was named" tests.</p>
     *
     * @param identifiers in the order they should be tried, which is the order
     *                    the marketplace stated them
     * @param skuCandidates fallbacks for the SKU when the caller gave none
     */
    private record SaveSource(
            String title,
            String brand,
            List<ProductIdentifierRequest> identifiers,
            BigDecimal advisoryPrice,
            String marketplace,
            String marketplaceItemId,
            List<String> skuCandidates) {

        List<ProductIdentifierRequest> identifierRequests() {
            return identifiers.isEmpty() ? null : identifiers;
        }
    }

    /** The spreadsheet row's own values — the behaviour before listings could be named. */
    private SaveSource rowSource(BulkSearchRow row) {
        return new SaveSource(
                firstNonBlank(row.getInputTitle(), row.getResolvedQuery()),
                row.getInputBrand(),
                identifiersOfList(row),
                topMatchPrice(row),
                null,
                null,
                List.of(nullToEmpty(row.getInputSku()), nullToEmpty(row.getInputAsin()),
                        nullToEmpty(row.getInputUpc()), nullToEmpty(row.getInputEan())));
    }

    /**
     * One matched listing's values.
     *
     * <p>Refuses a listing this row did not match. The row id and the item id
     * both arrive from the caller, and a product built from a listing belonging
     * to some other search would be wrong in a way nobody would notice.</p>
     */
    private SaveSource listingSource(BulkSearchRow row, String marketplace, String marketplaceItemId) {
        JsonNode listing = findListing(row, marketplace, marketplaceItemId);
        if (listing == null) {
            throw new BadRequestException("That listing isn't one of this row's matches.");
        }

        String itemId = listing.path("marketplaceItemId").asText("");
        String market = listing.path("marketplace").asText(marketplace).toUpperCase();
        boolean amazon = "AMAZON".equals(market);

        List<ProductIdentifierRequest> identifiers = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonNode identifier : listing.path("identifiers")) {
            String rawType = identifier.path("type").asText("");
            String value = identifier.path("value").asText("");
            if (rawType.isBlank() || value.isBlank()) {
                continue;
            }
            try {
                IdentifierType type = IdentifierType.valueOf(rawType.toUpperCase());
                // A listing legitimately states several barcodes, so values are
                // kept per type rather than one per type — but the same code
                // twice would fail the catalogue's uniqueness check.
                if (seen.add(type + "|" + value.trim())) {
                    identifiers.add(ProductIdentifierRequest.builder()
                            .type(type).originalValue(value.trim()).marketplace(market).build());
                }
            } catch (IllegalArgumentException unknownType) {
                // ISBN and anything else not modelled. Ignored rather than
                // guessed at: a wrong type is worse than a missing one.
            }
        }
        if (!itemId.isBlank()) {
            // On Amazon the listing id IS the ASIN. Elsewhere it identifies the
            // listing but not the product, so it is stored as what it is.
            IdentifierType type = amazon ? IdentifierType.ASIN : IdentifierType.MARKETPLACE_ID;
            if (seen.add(type + "|" + itemId.trim())) {
                identifiers.add(ProductIdentifierRequest.builder()
                        .type(type).originalValue(itemId.trim()).marketplace(market).build());
            }
        }

        return new SaveSource(
                firstNonBlank(listing.path("title").asText(null), row.getInputTitle()),
                // Amazon puts the brand in this field; eBay puts the merchant
                // there, and a merchant name is not a brand.
                amazon ? listing.path("seller").asText(null) : null,
                identifiers,
                listing.path("price").isNumber() ? listing.get("price").decimalValue() : null,
                market,
                itemId.isBlank() ? marketplaceItemId : itemId,
                skuCandidatesOf(identifiers, itemId));
    }

    /** ASIN first, then a barcode, then the listing's own id — per the frontend's rules. */
    private static List<String> skuCandidatesOf(List<ProductIdentifierRequest> identifiers, String itemId) {
        List<String> candidates = new ArrayList<>();
        for (IdentifierType preferred : List.of(IdentifierType.ASIN, IdentifierType.UPC,
                IdentifierType.EAN, IdentifierType.GTIN)) {
            identifiers.stream()
                    .filter(i -> i.getType() == preferred)
                    .findFirst()
                    .ifPresent(i -> candidates.add(i.getOriginalValue()));
        }
        candidates.add(nullToEmpty(itemId));
        return candidates;
    }

    /** The row's matched listing with this marketplace and item id, or null. */
    private JsonNode findListing(BulkSearchRow row, String marketplace, String marketplaceItemId) {
        if (row.getMatchesJson() == null || row.getMatchesJson().isBlank()) {
            return null;
        }
        try {
            JsonNode matches = objectMapper.readTree(row.getMatchesJson());
            if (!matches.isArray()) {
                return null;
            }
            for (JsonNode listing : matches) {
                if (marketplace.equalsIgnoreCase(listing.path("marketplace").asText(""))
                        && marketplaceItemId.equals(listing.path("marketplaceItemId").asText(""))) {
                    return listing;
                }
            }
        } catch (Exception e) {
            log.debug("Could not read matches for row {}: {}", row.getId(), e.getMessage());
        }
        return null;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * Finds the catalogue product this row already refers to.
     *
     * <p>Identifiers are checked before the SKU: a barcode identifies the
     * product itself, while a SKU is only the label one seller put on it. Two
     * rows with different SKUs and the same ASIN are the same product.</p>
     */
    private Match findExisting(SaveSource source, String sku) {
        for (ProductIdentifierRequest candidate : source.identifiers()) {
            String normalised = IdentifierNormalizer.normalize(
                    candidate.getType(), candidate.getOriginalValue());
            for (ProductIdentifier pi : identifierRepo
                    .findByTypeAndNormalizedValue(candidate.getType(), normalised)) {
                Product p = pi.getProduct();
                if (p != null && inTenant(p)) {
                    return new Match(p, candidate.getType() + " " + candidate.getOriginalValue());
                }
            }
        }

        String candidateSku = firstNonBlank(sku,
                source.skuCandidates().isEmpty() ? null : source.skuCandidates().get(0));
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
    private SaveRowResponse compareOrUpdate(BulkSearchRow row, SaveSource source, Product product,
                                            String matchedOn, boolean update) {
        List<SaveRowResponse.FieldDifference> diffs = new ArrayList<>();

        addDiff(diffs, "title", product.getTitle(), source.title(), false);
        addDiff(diffs, "brand", product.getBrand(), source.brand(), false);

        // The marketplace price is what a seller charges on Amazon, not
        // necessarily what this catalogue should sell at — flagged advisory so
        // the dialog can mark it apart from a plain title correction.
        BigDecimal marketPrice = source.advisoryPrice();
        addDiff(diffs, "ourPrice",
                product.getOurPrice() == null ? null : product.getOurPrice().toPlainString(),
                marketPrice == null ? null : marketPrice.toPlainString(), true);

        // Identifiers the row carries that differ from the product's — both the
        // ones it lacks entirely and the ones it holds a different value for.
        // An identifier type the row says nothing about is never touched.
        Map<IdentifierType, String> existing = new LinkedHashMap<>();
        Set<String> existingPairs = new HashSet<>();
        identifierRepo.findByProductId(product.getId()).forEach(pi -> {
            existing.putIfAbsent(pi.getType(), pi.getNormalizedValue());
            existingPairs.add(pi.getType() + "|" + pi.getNormalizedValue());
        });
        for (ProductIdentifierRequest req : source.identifiers()) {
            String normalised = IdentifierNormalizer.normalize(req.getType(), req.getOriginalValue());
            // Compared by value, not just by type: a listing states several
            // barcodes of one type, and judging only the first would report a
            // difference for every one of them.
            if (!existingPairs.contains(req.getType() + "|" + normalised)) {
                addDiff(diffs, "identifier." + req.getType(),
                        existing.get(req.getType()), req.getOriginalValue(), false);
            }
        }

        if (diffs.isEmpty()) {
            return response(source, product, matchedOn, "UNCHANGED",
                    "Already in your catalogue, and nothing has changed.", List.of());
        }
        if (!update) {
            return response(source, product, matchedOn, "EXISTS",
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
        // Keyed by type and value so a listing's several barcodes all survive;
        // keying by type alone would keep one and silently drop the rest.
        Map<String, ProductIdentifierRequest> merged = new LinkedHashMap<>();
        identifierRepo.findByProductId(product.getId()).forEach(pi -> merged.put(
                pi.getType() + "|" + pi.getNormalizedValue(),
                ProductIdentifierRequest.builder()
                        .type(pi.getType()).originalValue(pi.getOriginalValue()).build()));
        source.identifiers().forEach(incoming -> merged.put(
                incoming.getType() + "|"
                        + IdentifierNormalizer.normalize(incoming.getType(), incoming.getOriginalValue()),
                incoming));
        req.identifiers(new ArrayList<>(merged.values()));

        var updated = productService.updateProduct(product.getId(), req.build());
        if (source.marketplace() == null) {
            row.setSavedProductId(updated.getId());
            rowRepo.save(row);
        }
        log.info("Bulk search row {} updated product {} ({} field(s))",
                row.getId(), updated.getId(), diffs.size());

        return response(source, product, matchedOn, "UPDATED",
                "Updated " + diffs.size() + " detail(s) on \"" + updated.getTitle() + "\".", diffs);
    }

    private SaveRowResponse response(SaveSource source, Product product, String matchedOn,
            String status, String message, List<SaveRowResponse.FieldDifference> diffs) {
        return SaveRowResponse.builder()
                .status(status)
                .productId(product.getId())
                .productSku(product.getSku())
                .productTitle(product.getTitle())
                .marketplace(source.marketplace())
                .marketplaceItemId(source.marketplaceItemId())
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

    private List<ProductIdentifierRequest> identifiersOfList(BulkSearchRow row) {
        List<ProductIdentifierRequest> list = new ArrayList<>();
        addIdentifier(list, IdentifierType.ASIN, row.getInputAsin());
        addIdentifier(list, IdentifierType.UPC, row.getInputUpc());
        addIdentifier(list, IdentifierType.EAN, row.getInputEan());
        addIdentifier(list, IdentifierType.GTIN, row.getInputGtin());
        addIdentifier(list, IdentifierType.MPN, row.getInputMpn());
        return list;
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

    /**
     * The file's Region column, or null when it has none.
     *
     * <p>Matched outside {@link #autoMap} so that the product import's
     * {@code country} alias — country of origin — cannot be read as a
     * storefront.</p>
     */
    private String regionHeaderIn(List<String> headers) {
        for (String header : headers) {
            if (REGION_ALIASES.contains(ImportFieldCatalog.normalizeKey(header))) {
                return header;
            }
        }
        return null;
    }

    /**
     * A region as the rest of the system spells it, or null when none was given.
     *
     * <p>Rejected rather than defaulted when it is not a country: {@code UKK}
     * silently becoming the US default would search the wrong catalogue for
     * every row in the file and report the results as the truth.</p>
     *
     * @param where what to name in the error — "region", or the row number
     */
    private String normalisedRegion(String region, String where) {
        if (region == null || region.isBlank()) {
            return null;
        }
        String code = Storefront.normalise(region);
        if (code == null) {
            throw new BadRequestException("Unknown " + where + ": " + region.trim()
                    + ". Use a two-letter country code such as US, CA or GB.");
        }
        return code;
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
