package com.priceintel.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import com.priceintel.backend.entity.User;

@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Active (non-deleted) user by email — used for login. */
    Optional<User> findByEmailAndStatusNot(String email, com.priceintel.backend.constants.UserStatus status);

    /** Count non-deleted users in a tenant (for max-users enforcement). */
    long countByTenantIdAndStatusNot(Long tenantId, com.priceintel.backend.constants.UserStatus status);

    Optional<User> findByIdAndTenantId(Long id, Long tenantId);

    /** All users of a tenant (for notifying a client's team). */
    java.util.List<User> findByTenantId(Long tenantId);

    /** Platform owners (for notifying the super admin). */
    java.util.List<User> findBySuperAdminTrue();
}
