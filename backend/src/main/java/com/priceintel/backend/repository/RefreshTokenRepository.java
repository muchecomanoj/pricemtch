package com.priceintel.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.priceintel.backend.entity.RefreshToken;
import com.priceintel.backend.entity.User;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByToken(String token);

    @Modifying
    @Query("delete from RefreshToken rt where rt.user = :user")
    void deleteByUser(User user);

    @Modifying
    @Query("delete from RefreshToken rt where rt.token = :token")
    void deleteByToken(String token);
}
