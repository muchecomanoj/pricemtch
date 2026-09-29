package com.priceintel.backend.service.impl;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.priceintel.backend.constants.AccessType;
import com.priceintel.backend.constants.SubscriptionAction;
import com.priceintel.backend.constants.SubscriptionStatus;
import com.priceintel.backend.constants.TenantStatus;
import com.priceintel.backend.constants.UserStatus;
import com.priceintel.backend.dto.request.CreateTenantRequest;
import com.priceintel.backend.dto.request.UpdateTenantRequest;
import com.priceintel.backend.dto.response.PagedResponse;
import com.priceintel.backend.dto.response.TenantResponse;
import com.priceintel.backend.dto.response.TenantStatsResponse;
import com.priceintel.backend.entity.SubscriptionHistory;
import com.priceintel.backend.entity.SubscriptionPlan;
import com.priceintel.backend.entity.Tenant;
import com.priceintel.backend.entity.User;
import com.priceintel.backend.exception.BadRequestException;
import com.priceintel.backend.exception.DuplicateResourceException;
import com.priceintel.backend.exception.ResourceNotFoundException;
import com.priceintel.backend.mapper.TenantMapper;
import com.priceintel.backend.repository.SubscriptionHistoryRepository;
import com.priceintel.backend.repository.SubscriptionPlanRepository;
import com.priceintel.backend.repository.TenantRepository;
import com.priceintel.backend.repository.UserRepository;
import com.priceintel.backend.service.TenantService;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantServiceImpl implements TenantService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final SubscriptionPlanRepository planRepository;
    private final SubscriptionHistoryRepository historyRepository;
    private final PasswordEncoder passwordEncoder;
    private final TenantMapper tenantMapper;
    private final com.priceintel.backend.service.OnboardingService onboardingService;
    private final SubscriptionAccessService accessService;

    private static final java.util.Set<String> SORTABLE =
            java.util.Set.of("id", "companyName", "companyCode", "status", "createdAt");

    @Override
    @Transactional
    public TenantResponse createTenant(CreateTenantRequest request) {
        // Derive anything the caller didn't supply.
        String companyCode = resolveCompanyCode(request);
        String adminEmail = hasText(request.getAdminEmail())
                ? request.getAdminEmail().trim() : request.getCompanyEmail().trim();
        String[] adminName = resolveAdminName(request);
        // The client sets their own password during activation; until then the
        // account holds an unusable random password and cannot log in.
        boolean selfActivate = !hasText(request.getAdminPassword());
        String adminPassword = selfActivate ? generateTemporaryPassword() : request.getAdminPassword();

        validateUniqueness(request, companyCode, adminEmail);

        Tenant tenant = Tenant.builder()
                .companyName(request.getCompanyName())
                .companyCode(companyCode)
                .companyEmail(request.getCompanyEmail())
                .companyPhone(request.getCompanyPhone())
                .companyAddress(request.getCompanyAddress())
                .city(request.getCity()).state(request.getState()).country(request.getCountry())
                .postalCode(request.getPostalCode()).timezone(request.getTimezone())
                .currency(request.getCurrency()).website(request.getWebsite())
                .logo(asLogoUrl(request.getLogo()))
                .contactPerson(request.getContactPerson())
                .industry(request.getIndustry())
                // Live only once the client activates via the emailed link.
                .status(selfActivate ? TenantStatus.PENDING_ACTIVATION : TenantStatus.ACTIVE)
                .build();

        applyPlan(tenant, request.getSubscriptionPlan(), request.getTrialDays());
        // Remember the billing cycle the super admin chose so the client's
        // onboarding wizard pre-selects Monthly/Yearly as picked.
        if (request.getBillingCycle() != null) {
            tenant.setBillingCycle(request.getBillingCycle());
        }
        // An explicit user limit overrides the plan's default.
        if (request.getMaxUsers() != null && request.getMaxUsers() > 0) {
            tenant.setMaxUsers(request.getMaxUsers());
        }
        tenant = tenantRepository.save(tenant);

        // Create the first Tenant Administrator (the "Client" login).
        User admin = User.builder()
                .tenantId(tenant.getId())
                .superAdmin(false)
                .firstName(adminName[0])
                .lastName(adminName[1])
                .email(adminEmail)
                .password(passwordEncoder.encode(adminPassword))
                .accessType(AccessType.ADMIN)
                .status(selfActivate ? UserStatus.PENDING : UserStatus.ACTIVE)
                .build();
        admin = userRepository.save(admin);

        historyRepository.save(SubscriptionHistory.builder()
                .tenantId(tenant.getId()).action(SubscriptionAction.ASSIGNED)
                .planCode(tenant.getSubscriptionPlan()).status(tenant.getSubscriptionStatus())
                .startDate(tenant.getSubscriptionStartDate()).endDate(tenant.getSubscriptionEndDate())
                .note("Tenant created").build());

        log.info("Created tenant {} ({}) with admin {}", tenant.getCompanyName(),
                tenant.getCompanyCode(), admin.getEmail());

        TenantResponse response = tenantMapper.toResponse(tenant);
        response.setAdminEmail(adminEmail);

        if (selfActivate) {
            // Email the client an activation link + verification code. They set
            // their own password, so no password is ever emailed or returned.
            onboardingService.issueActivation(tenant, admin);
            response.setActivationEmailSent(true);
        }
        return response;
    }

    /** Uses the supplied code, else derives a unique one from the company name. */
    private String resolveCompanyCode(CreateTenantRequest request) {
        if (hasText(request.getCompanyCode())) {
            return request.getCompanyCode().trim().toUpperCase();
        }
        String base = request.getCompanyName().toUpperCase().replaceAll("[^A-Z0-9]", "");
        if (base.isEmpty()) {
            base = "TENANT";
        }
        base = base.substring(0, Math.min(base.length(), 12));
        String candidate = base;
        int suffix = 1;
        while (tenantRepository.existsByCompanyCodeIgnoreCase(candidate)) {
            candidate = base + (++suffix);
        }
        return candidate;
    }

    /** Splits contactPerson into first/last, falling back to sensible defaults. */
    private String[] resolveAdminName(CreateTenantRequest request) {
        String first = request.getAdminFirstName();
        String last = request.getAdminLastName();
        if (hasText(first) && hasText(last)) {
            return new String[]{first.trim(), last.trim()};
        }
        if (hasText(request.getContactPerson())) {
            String[] parts = request.getContactPerson().trim().split("\\s+", 2);
            return new String[]{parts[0], parts.length > 1 ? parts[1] : "Admin"};
        }
        return new String[]{hasText(first) ? first.trim() : "Account",
                hasText(last) ? last.trim() : "Admin"};
    }

    /** Only a string logo is meaningful; other JSON shapes are ignored. */
    private String asLogoUrl(Object logo) {
        return (logo instanceof String s && !s.isBlank()) ? s : null;
    }

    /** Temporary password that satisfies the platform password policy. */
    private String generateTemporaryPassword() {
        final String upper = "ABCDEFGHJKLMNPQRSTUVWXYZ";
        final String lower = "abcdefghijkmnopqrstuvwxyz";
        final String digits = "23456789";
        final String special = "@#$%&*";
        java.security.SecureRandom rnd = new java.security.SecureRandom();
        StringBuilder sb = new StringBuilder();
        sb.append(upper.charAt(rnd.nextInt(upper.length())));
        sb.append(lower.charAt(rnd.nextInt(lower.length())));
        sb.append(digits.charAt(rnd.nextInt(digits.length())));
        sb.append(special.charAt(rnd.nextInt(special.length())));
        String all = upper + lower + digits;
        for (int i = 0; i < 8; i++) {
            sb.append(all.charAt(rnd.nextInt(all.length())));
        }
        return sb.toString();
    }

    private boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private void applyPlan(Tenant tenant, String planCode, Integer trialDays) {
        LocalDate today = LocalDate.now();
        if (planCode != null && !planCode.isBlank()) {
            SubscriptionPlan plan = planRepository.findByCodeIgnoreCase(planCode)
                    .orElseThrow(() -> new BadRequestException(
                            "Unknown subscription plan: " + planCode
                                    + ". Call GET /api/v1/admin/plans for valid codes."));
            tenant.setSubscriptionPlan(plan.getCode());
            tenant.setMaxUsers(plan.getMaxUsers());
            tenant.setSubscriptionStartDate(today);
            tenant.setSubscriptionEndDate(today.plusDays(plan.getBillingCycleDays()));
            tenant.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
        } else {
            int trial = (trialDays != null && trialDays > 0) ? trialDays : 14;
            tenant.setSubscriptionStatus(SubscriptionStatus.TRIAL);
            tenant.setSubscriptionStartDate(today);
            tenant.setSubscriptionEndDate(today.plusDays(trial));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public TenantResponse getTenant(Long id) {
        return tenantMapper.toResponse(findActive(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<TenantResponse> listTenants(String keyword, int page, int size,
                                                     String sortBy, String direction) {
        Pageable pageable = buildPageable(page, size, sortBy, direction);
        Specification<Tenant> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.notEqual(root.get("status"), TenantStatus.DELETED));
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim().toLowerCase() + "%";
                ps.add(cb.or(
                        cb.like(cb.lower(root.get("companyName")), like),
                        cb.like(cb.lower(root.get("companyCode")), like),
                        cb.like(cb.lower(root.get("companyEmail")), like)));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
        Page<TenantResponse> result = tenantRepository.findAll(spec, pageable).map(tenantMapper::toResponse);
        return PagedResponse.from(result);
    }

    @Override
    @Transactional
    public TenantResponse updateTenant(Long id, UpdateTenantRequest request) {
        Tenant tenant = findActive(id);
        if (request.getCompanyName() != null
                && !request.getCompanyName().equalsIgnoreCase(tenant.getCompanyName())
                && tenantRepository.existsByCompanyNameIgnoreCase(request.getCompanyName())) {
            throw new DuplicateResourceException("Company name already in use");
        }
        tenantMapper.updateTenantFromRequest(request, tenant);
        TenantResponse saved = tenantMapper.toResponse(tenantRepository.save(tenant));
        accessService.evict(id);
        return saved;
    }

    @Override
    @Transactional
    public TenantResponse activate(Long id) {
        return setStatus(id, TenantStatus.ACTIVE);
    }

    @Override
    @Transactional
    public TenantResponse deactivate(Long id) {
        return setStatus(id, TenantStatus.INACTIVE);
    }

    @Override
    @Transactional
    public TenantResponse suspend(Long id) {
        return setStatus(id, TenantStatus.SUSPENDED);
    }

    @Override
    @Transactional
    public TenantResponse resume(Long id) {
        return setStatus(id, TenantStatus.ACTIVE);
    }

    @Override
    @Transactional
    public void softDelete(Long id) {
        Tenant tenant = findActive(id);
        tenant.setStatus(TenantStatus.DELETED);
        tenantRepository.save(tenant);
        accessService.evict(id);
        log.info("Soft-deleted tenant id={}", id);
    }

    @Override
    @Transactional(readOnly = true)
    public TenantStatsResponse getStats(Long id) {
        Tenant tenant = findActive(id);
        long total = userRepository.countByTenantIdAndStatusNot(id, UserStatus.DELETED);
        long active = userRepository.count(
                com.priceintel.backend.repository.UserSpecifications.withFilters(id, null, null, UserStatus.ACTIVE));
        return TenantStatsResponse.builder()
                .tenantId(id).companyName(tenant.getCompanyName())
                .totalUsers(total).activeUsers(active).maxUsers(tenant.getMaxUsers())
                .subscriptionPlan(tenant.getSubscriptionPlan())
                .subscriptionStatus(String.valueOf(tenant.getSubscriptionStatus()))
                .tenantStatus(tenant.getStatus().name())
                .build();
    }

    // ---------- helpers ----------

    private TenantResponse setStatus(Long id, TenantStatus status) {
        Tenant tenant = findActive(id);
        tenant.setStatus(status);
        log.info("Tenant {} status -> {}", id, status);
        TenantResponse saved = tenantMapper.toResponse(tenantRepository.save(tenant));
        // Suspend and resume take effect on the company's next request.
        accessService.evict(id);
        return saved;
    }

    private Tenant findActive(Long id) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found with id: " + id));
        if (tenant.getStatus() == TenantStatus.DELETED) {
            throw new ResourceNotFoundException("Tenant not found with id: " + id);
        }
        return tenant;
    }

    private void validateUniqueness(CreateTenantRequest r, String companyCode, String adminEmail) {
        if (tenantRepository.existsByCompanyCodeIgnoreCase(companyCode)) {
            throw new DuplicateResourceException("Company code already in use: " + companyCode);
        }
        if (tenantRepository.existsByCompanyEmailIgnoreCase(r.getCompanyEmail())) {
            throw new DuplicateResourceException("Company email already in use: " + r.getCompanyEmail());
        }
        if (tenantRepository.existsByCompanyNameIgnoreCase(r.getCompanyName())) {
            throw new DuplicateResourceException("Company name already in use: " + r.getCompanyName());
        }
        if (userRepository.existsByEmail(adminEmail)) {
            throw new DuplicateResourceException("Admin email already registered: " + adminEmail);
        }
    }

    private Pageable buildPageable(int page, int size, String sortBy, String direction) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BadRequestException("Invalid pagination parameters");
        }
        String sortField = SORTABLE.contains(sortBy) ? sortBy : "createdAt";
        Sort.Direction dir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return PageRequest.of(page, size, Sort.by(dir, sortField));
    }
}
