package com.priceintel.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.priceintel.backend.entity.PasswordResetToken;
import com.priceintel.backend.entity.User;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByToken(String token);

    /** The user's most recent unused reset grant. */
    Optional<PasswordResetToken> findTopByUserAndUsedFalseOrderByIdDesc(User user);

    @Modifying
    @Query("delete from PasswordResetToken t where t.user = :user")
    void deleteByUser(User user);
}
