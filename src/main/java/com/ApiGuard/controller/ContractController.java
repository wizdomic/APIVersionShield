package com.ApiGuard.controller;

import com.ApiGuard.model.ApiContract;
import com.ApiGuard.model.GuardCheckRequest;
import com.ApiGuard.service.ContractService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/contracts")
public class ContractController {

    private final ContractService contractService;

    public ContractController(ContractService contractService) {
        this.contractService = contractService;
    }

    // Manual upload — projectId + version required
    @PostMapping("/upload")
    public ResponseEntity<String> uploadContract(@Valid @RequestBody ApiContract contract) {
        contractService.addContract(contract);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body("Contract uploaded for project: " + contract.getProjectId()
                        + ", version: " + contract.getVersion());
    }

    // Force accept a breaking schema as new baseline
    @PostMapping("/accept")
    public ResponseEntity<String> acceptContract(@Valid @RequestBody GuardCheckRequest request) {
        contractService.acceptContract(request.getProjectId(), request.getSchema());
        return ResponseEntity.ok("Schema accepted as new baseline for project: " + request.getProjectId());
    }

    // Validate a payload against a project's contract
    @PostMapping("/validate")
    public ResponseEntity<String> validatePayload(
            @RequestParam String projectId,
            @RequestParam(required = false) String version,
            @RequestBody String payload) {
        try {
            contractService.validatePayload(projectId, version, payload);
            return ResponseEntity.ok("Payload is valid!");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Payload validation failed: " + e.getMessage());
        }
    }

    // List contracts — optionally filter by projectId
    @GetMapping
    public ResponseEntity<?> getContracts(@RequestParam(required = false) String projectId) {
        return ResponseEntity.ok(contractService.getContracts(projectId));
    }
}
