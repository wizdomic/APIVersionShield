package com.ApiGuard.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface GuardAuditLogRepository extends JpaRepository<GuardAuditLog, Long> {

    @Query("SELECT g FROM GuardAuditLog g ORDER BY g.checkedAt DESC")
    List<GuardAuditLog> findAllOrderedByCheckedAtDesc();
}