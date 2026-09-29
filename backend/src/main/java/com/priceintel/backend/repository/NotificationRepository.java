package com.priceintel.backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.priceintel.backend.entity.Notification;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByRecipientUserIdOrderByCreatedAtDesc(Long recipientUserId, Pageable pageable);

    long countByRecipientUserIdAndReadFalse(Long recipientUserId);

    @Modifying
    @Query("update Notification n set n.read = true, n.readAt = CURRENT_TIMESTAMP "
            + "where n.recipientUserId = :userId and n.read = false")
    int markAllRead(@Param("userId") Long userId);
}
