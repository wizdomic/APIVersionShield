package com.ApiGuard.controller;

//import com.ApiGuard.audit.GuardAuditLog;
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
    GuardCheckResponse response = contractService.guardCheck(request.getProjectId(), request.getSchema());
    return ResponseEntity.ok(response);
  }

  // audit log temporarily disabled
  /*
   * @GetMapping("/audit")
   * public ResponseEntity<List<GuardAuditLog>> getAuditLog(
   * 
   * @RequestParam(required = false) String projectId) {
   * return ResponseEntity.ok(contractService.getAuditLog(projectId));
   * }
   */
}
