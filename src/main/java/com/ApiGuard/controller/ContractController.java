package com.ApiGuard.controller;

import com.ApiGuard.model.ApiContract;
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

    @PostMapping("/upload")
    public ResponseEntity<String> uploadContract(@Valid @RequestBody ApiContract contract) {
        contractService.addContract(contract);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body("Contract uploaded for version: " + contract.getVersion());
    }

    @PostMapping("/validate")
    public ResponseEntity<String> validatePayload(
            @RequestParam String version,
            @RequestBody String payload) {
        try {
            contractService.validatePayload(version, payload);
            return ResponseEntity.ok("Payload is valid!");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Payload validation failed: " + e.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<?> getContracts() {
        return ResponseEntity.ok(contractService.getContracts());
    }
}