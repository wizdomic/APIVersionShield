package com.ApiGuard.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GuardAuditLogRepository extends JpaRepository<GuardAuditLog, Long> {

    @Query("SELECT g FROM GuardAuditLog g ORDER BY g.checkedAt DESC")
    List<GuardAuditLog> findAllOrderedByCheckedAtDesc();

    @Query("SELECT g FROM GuardAuditLog g WHERE g.projectId = :projectId ORDER BY g.checkedAt DESC")
    List<GuardAuditLog> findAllByProjectIdOrderByCheckedAtDesc(@Param("projectId") String projectId);
}
