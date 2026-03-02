package com.ApiGuard.service;

import com.ApiGuard.audit.GuardAuditLog;
import com.ApiGuard.audit.GuardAuditLogRepository;
import com.ApiGuard.model.ApiContract;
import com.ApiGuard.model.DeploymentDecision;
import com.ApiGuard.model.GuardCheckResponse;
import com.ApiGuard.repository.ApiContractRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ContractServiceTest {

    private ApiContractRepository contractRepository;
    private GuardAuditLogRepository auditLogRepository;
    private ContractService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        contractRepository = Mockito.mock(ApiContractRepository.class);
        auditLogRepository = Mockito.mock(GuardAuditLogRepository.class);
        objectMapper = new ObjectMapper();
        service = new ContractService(contractRepository, auditLogRepository, objectMapper);

        when(auditLogRepository.save(any(GuardAuditLog.class)))
                .thenAnswer(i -> i.getArgument(0));
    }

    private ApiContract buildContract(String version, String schemaJson) throws Exception {
        ApiContract c = new ApiContract();
        c.setVersion(version);
        c.setSchema(objectMapper.readTree(schemaJson));
        return c;
    }

    // ─── evaluateDeployment ──────────────────────────────────────────────────

    @Test
    void shouldReturnSafeWhenNoChanges() throws Exception {
        String schema = """
                {"type":"object","required":["name"],"properties":{"name":{"type":"string"}}}
                """;
        ApiContract v1 = buildContract("1.0.0", schema);
        ApiContract v2 = buildContract("2.0.0", schema);

        when(contractRepository.findByVersion("1.0.0")).thenReturn(Optional.of(v1));
        when(contractRepository.findByVersion("2.0.0")).thenReturn(Optional.of(v2));

        GuardCheckResponse response = service.evaluateDeployment("1.0.0", "2.0.0");
        assertEquals(DeploymentDecision.SAFE_TO_DEPLOY, response.getDecision());
    }

    @Test
    void shouldBlockWhenRequiredFieldRemoved() throws Exception {
        ApiContract v1 = buildContract("1.0.0",
                """
                {"type":"object","required":["name","email"],"properties":{"name":{"type":"string"},"email":{"type":"string"}}}
                """);
        ApiContract v2 = buildContract("2.0.0",
                """
                {"type":"object","required":["name"],"properties":{"name":{"type":"string"}}}
                """);

        when(contractRepository.findByVersion("1.0.0")).thenReturn(Optional.of(v1));
        when(contractRepository.findByVersion("2.0.0")).thenReturn(Optional.of(v2));

        GuardCheckResponse response = service.evaluateDeployment("1.0.0", "2.0.0");
        assertEquals(DeploymentDecision.BLOCK_DEPLOYMENT, response.getDecision());
        assertTrue(response.getChanges().stream().anyMatch(c -> c.contains("email")));
    }

    @Test
    void shouldBlockWhenFieldTypeChanges() throws Exception {
        ApiContract v1 = buildContract("1.0.0",
                """
                {"type":"object","required":["age"],"properties":{"age":{"type":"string"}}}
                """);
        ApiContract v2 = buildContract("2.0.0",
                """
                {"type":"object","required":["age"],"properties":{"age":{"type":"integer"}}}
                """);

        when(contractRepository.findByVersion("1.0.0")).thenReturn(Optional.of(v1));
        when(contractRepository.findByVersion("2.0.0")).thenReturn(Optional.of(v2));

        GuardCheckResponse response = service.evaluateDeployment("1.0.0", "2.0.0");
        assertEquals(DeploymentDecision.BLOCK_DEPLOYMENT, response.getDecision());
        assertTrue(response.getChanges().stream().anyMatch(c -> c.contains("string") && c.contains("integer")));
    }

    @Test
    void shouldWarnWhenNewRequiredFieldAdded() throws Exception {
        ApiContract v1 = buildContract("1.0.0",
                """
                {"type":"object","required":["name"],"properties":{"name":{"type":"string"}}}
                """);
        ApiContract v2 = buildContract("2.0.0",
                """
                {"type":"object","required":["name","phone"],"properties":{"name":{"type":"string"},"phone":{"type":"string"}}}
                """);

        when(contractRepository.findByVersion("1.0.0")).thenReturn(Optional.of(v1));
        when(contractRepository.findByVersion("2.0.0")).thenReturn(Optional.of(v2));

        GuardCheckResponse response = service.evaluateDeployment("1.0.0", "2.0.0");
        assertEquals(DeploymentDecision.WARN_ONLY, response.getDecision());
    }

    @Test
    void shouldBlockWhenContractNotFound() {
        when(contractRepository.findByVersion(any())).thenReturn(Optional.empty());
        GuardCheckResponse response = service.evaluateDeployment("1.0.0", "9.9.9");
        assertEquals(DeploymentDecision.BLOCK_DEPLOYMENT, response.getDecision());
    }

    @Test
    void shouldCollectAllBreakingChangesNotJustFirst() throws Exception {
        ApiContract v1 = buildContract("1.0.0",
                """
                {"type":"object","required":["name","email"],"properties":{"name":{"type":"string"},"email":{"type":"string"}}}
                """);
        ApiContract v2 = buildContract("2.0.0",
                """
                {"type":"object","required":[],"properties":{"name":{"type":"integer"}}}
                """);

        when(contractRepository.findByVersion("1.0.0")).thenReturn(Optional.of(v1));
        when(contractRepository.findByVersion("2.0.0")).thenReturn(Optional.of(v2));

        GuardCheckResponse response = service.evaluateDeployment("1.0.0", "2.0.0");
        assertEquals(DeploymentDecision.BLOCK_DEPLOYMENT, response.getDecision());
        assertTrue(response.getChanges().size() > 1, "Should collect multiple breaking changes");
    }

    // ─── addContract ─────────────────────────────────────────────────────────

    @Test
    void shouldThrowWhenDuplicateVersionAdded() throws Exception {
        ApiContract existing = buildContract("1.0.0",
                """
                {"type":"object","required":["name"],"properties":{"name":{"type":"string"}}}
                """);
        when(contractRepository.findByVersion("1.0.0")).thenReturn(Optional.of(existing));

        assertThrows(IllegalStateException.class, () -> service.addContract(existing));
    }

    @Test
    void shouldThrowWhenSchemaInvalid() throws Exception {
        ApiContract bad = new ApiContract();
        bad.setVersion("1.0.0");
        bad.setSchema(objectMapper.readTree("""
                {"noType": true, "required": []}
                """));

        assertThrows(IllegalArgumentException.class, () -> service.addContract(bad));
    }
}