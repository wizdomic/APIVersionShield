package com.ApiGuard.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class GuardCheckResponse {

    private DeploymentDecision decision;
    private String reason;
    private List<String> changes;

    // Two-arg constructor — defaults changes to empty list
    public GuardCheckResponse(DeploymentDecision decision, String reason) {
        this.decision = decision;
        this.reason = reason;
        this.changes = List.of();
    }

    // Three-arg constructor — full detail
    public GuardCheckResponse(DeploymentDecision decision, String reason, List<String> changes) {
        this.decision = decision;
        this.reason = reason;
        this.changes = changes;
    }
}