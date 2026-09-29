package com.priceintel.backend.repository;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import com.priceintel.backend.constants.AccessType;
import com.priceintel.backend.constants.UserStatus;
import com.priceintel.backend.entity.User;

import jakarta.persistence.criteria.Predicate;

/**
 * Composable filters for {@link User}. Always excludes soft-deleted users and,
 * when a tenantId is supplied, scopes strictly to that tenant.
 */
public final class UserSpecifications {

    private UserSpecifications() {
    }

    public static Specification<User> withFilters(Long tenantId, String keyword,
                                                  AccessType accessType, UserStatus status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Never surface soft-deleted users.
            predicates.add(cb.notEqual(root.get("status"), UserStatus.DELETED));

            // Tenant isolation: when scoped, only this tenant's users.
            if (tenantId != null) {
                predicates.add(cb.equal(root.get("tenantId"), tenantId));
            }

            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("email")), like),
                        cb.like(cb.lower(root.get("firstName")), like),
                        cb.like(cb.lower(root.get("lastName")), like)));
            }
            if (accessType != null) {
                predicates.add(cb.equal(root.get("accessType"), accessType));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
