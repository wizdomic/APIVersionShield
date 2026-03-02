package com.ApiGuard.audit;

import com.ApiGuard.model.DeploymentDecision;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "guard_audit_log")
@Getter
@Setter
public class GuardAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fromVersion;

    @Column(nullable = false)
    private String toVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeploymentDecision decision;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Column(nullable = false)
    private LocalDateTime checkedAt;

    // Required by JPA
    protected GuardAuditLog() {}

    // Used by ContractService
    public GuardAuditLog(String fromVersion, String toVersion, DeploymentDecision decision, String reason) {
        this.fromVersion = fromVersion;
        this.toVersion = toVersion;
        this.decision = decision;
        this.reason = reason;
        this.checkedAt = LocalDateTime.now();
    }
}