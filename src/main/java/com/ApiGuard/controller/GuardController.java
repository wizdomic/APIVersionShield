package com.ApiGuard.controller;

import com.ApiGuard.audit.GuardAuditLog;
import com.ApiGuard.model.GuardCheckRequest;
import com.ApiGuard.model.GuardCheckResponse;
import com.ApiGuard.service.ContractService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/guard")
public class GuardController {

    private final ContractService contractService;

    public GuardController(ContractService contractService) {
        this.contractService = contractService;
    }

    @PostMapping("/check")
    public ResponseEntity<GuardCheckResponse> guardCheck(@Valid @RequestBody GuardCheckRequest request) {
        GuardCheckResponse response = contractService.evaluateDeployment(request.getFrom(), request.getTo());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/audit")
    public ResponseEntity<List<GuardAuditLog>> getAuditLog() {
        return ResponseEntity.ok(contractService.getAuditLog());
    }
}