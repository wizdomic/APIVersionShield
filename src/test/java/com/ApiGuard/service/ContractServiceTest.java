package com.ApiGuard.service;

import com.ApiGuard.audit.GuardAuditLog;
import com.ApiGuard.audit.GuardAuditLogRepository;
import com.ApiGuard.model.ApiContract;
import com.ApiGuard.model.DeploymentDecision;
import com.ApiGuard.model.GuardCheckResponse;
import com.ApiGuard.repository.ApiContractRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
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
        when(contractRepository.save(any(ApiContract.class)))
                .thenAnswer(i -> i.getArgument(0));
    }

    private JsonNode schema(String json) throws Exception {
        return objectMapper.readTree(json);
    }

    private ApiContract baseline(String projectId, String schemaJson) throws Exception {
        ApiContract c = new ApiContract();
        c.setProjectId(projectId);
        c.setVersion("v-existing");
        c.setSchema(objectMapper.readTree(schemaJson));
        c.setBaseline(true);
        c.setCreatedAt(LocalDateTime.now());
        return c;
    }

    // ─── First schema ──────────────────────────────────────────────────────────

    @Test
    void shouldRegisterFirstSchemaAsBaselineAndReturnSafe() throws Exception {
        when(contractRepository.findByProjectIdAndBaselineTrue("project-a"))
                .thenReturn(Optional.empty());

        JsonNode s = schema("{\"type\":\"object\",\"required\":[\"name\"],\"properties\":{\"name\":{\"type\":\"string\"}}}");
        GuardCheckResponse response = service.guardCheck("project-a", s);

        assertEquals(DeploymentDecision.SAFE_TO_DEPLOY, response.getDecision());
        verify(contractRepository, times(1)).save(any(ApiContract.class));
    }

    // ─── SAFE ──────────────────────────────────────────────────────────────────

    @Test
    void shouldReturnSafeAndPromoteBaselineWhenNoChanges() throws Exception {
        String schemaJson = "{\"type\":\"object\",\"required\":[\"name\"],\"properties\":{\"name\":{\"type\":\"string\"}}}";
        ApiContract existing = baseline("project-a", schemaJson);
        when(contractRepository.findByProjectIdAndBaselineTrue("project-a"))
                .thenReturn(Optional.of(existing));

        GuardCheckResponse response = service.guardCheck("project-a", schema(schemaJson));

        assertEquals(DeploymentDecision.SAFE_TO_DEPLOY, response.getDecision());
        // old baseline unset + new baseline saved = 2 saves
        verify(contractRepository, times(2)).save(any(ApiContract.class));
    }

    // ─── WARN → baseline updated ───────────────────────────────────────────────

    @Test
    void shouldWarnAndPromoteBaselineOnNonBreakingChange() throws Exception {
        ApiContract existing = baseline("project-a",
                "{\"type\":\"object\",\"required\":[\"name\"],\"properties\":{\"name\":{\"type\":\"string\"}}}");
        when(contractRepository.findByProjectIdAndBaselineTrue("project-a"))
                .thenReturn(Optional.of(existing));

        JsonNode newSchema = schema(
                "{\"type\":\"object\",\"required\":[\"name\",\"phone\"],\"properties\":{\"name\":{\"type\":\"string\"},\"phone\":{\"type\":\"string\"}}}");
        GuardCheckResponse response = service.guardCheck("project-a", newSchema);

        assertEquals(DeploymentDecision.WARN_ONLY, response.getDecision());
        // WARN also promotes baseline
        verify(contractRepository, times(2)).save(any(ApiContract.class));
    }

    // ─── BLOCK → baseline unchanged ────────────────────────────────────────────

    @Test
    void shouldBlockAndKeepOldBaselineOnBreakingChange() throws Exception {
        ApiContract existing = baseline("project-a",
                "{\"type\":\"object\",\"required\":[\"name\",\"email\"],\"properties\":{\"name\":{\"type\":\"string\"},\"email\":{\"type\":\"string\"}}}");
        when(contractRepository.findByProjectIdAndBaselineTrue("project-a"))
                .thenReturn(Optional.of(existing));

        JsonNode breaking = schema(
                "{\"type\":\"object\",\"required\":[\"name\"],\"properties\":{\"name\":{\"type\":\"string\"}}}");
        GuardCheckResponse response = service.guardCheck("project-a", breaking);

        assertEquals(DeploymentDecision.BLOCK_DEPLOYMENT, response.getDecision());
        assertTrue(response.getChanges().stream().anyMatch(c -> c.contains("email")));
        // BLOCK — contractRepository.save never called (only auditLogRepository)
        verify(contractRepository, never()).save(any(ApiContract.class));
    }

    // ─── Type change → BLOCK ───────────────────────────────────────────────────

    @Test
    void shouldBlockOnTypeChange() throws Exception {
        ApiContract existing = baseline("project-a",
                "{\"type\":\"object\",\"required\":[\"age\"],\"properties\":{\"age\":{\"type\":\"string\"}}}");
        when(contractRepository.findByProjectIdAndBaselineTrue("project-a"))
                .thenReturn(Optional.of(existing));

        JsonNode changed = schema(
                "{\"type\":\"object\",\"required\":[\"age\"],\"properties\":{\"age\":{\"type\":\"integer\"}}}");
        GuardCheckResponse response = service.guardCheck("project-a", changed);

        assertEquals(DeploymentDecision.BLOCK_DEPLOYMENT, response.getDecision());
        assertTrue(response.getChanges().stream()
                .anyMatch(c -> c.contains("string") && c.contains("integer")));
    }

    // ─── All changes collected ──────────────────────────────────────────────────

    @Test
    void shouldCollectAllBreakingChangesNotJustFirst() throws Exception {
        ApiContract existing = baseline("project-a",
                "{\"type\":\"object\",\"required\":[\"name\",\"email\"],\"properties\":{\"name\":{\"type\":\"string\"},\"email\":{\"type\":\"string\"}}}");
        when(contractRepository.findByProjectIdAndBaselineTrue("project-a"))
                .thenReturn(Optional.of(existing));

        JsonNode breaking = schema(
                "{\"type\":\"object\",\"required\":[],\"properties\":{\"name\":{\"type\":\"integer\"}}}");
        GuardCheckResponse response = service.guardCheck("project-a", breaking);

        assertEquals(DeploymentDecision.BLOCK_DEPLOYMENT, response.getDecision());
        assertTrue(response.getChanges().size() > 1, "Should report all changes, not just first");
    }

    // ─── Project isolation ─────────────────────────────────────────────────────

    @Test
    void shouldIsolateDifferentProjects() throws Exception {
        String schemaAJson = "{\"type\":\"object\",\"required\":[\"fieldA\"],\"properties\":{\"fieldA\":{\"type\":\"string\"}}}";
        String schemaBJson = "{\"type\":\"object\",\"required\":[\"fieldB\"],\"properties\":{\"fieldB\":{\"type\":\"string\"}}}";

        ApiContract baselineA = baseline("project-a", schemaAJson);
        when(contractRepository.findByProjectIdAndBaselineTrue("project-a"))
                .thenReturn(Optional.of(baselineA));
        when(contractRepository.findByProjectIdAndBaselineTrue("project-b"))
                .thenReturn(Optional.empty());

        // Project B has no baseline → SAFE (first schema)
        GuardCheckResponse responseB = service.guardCheck("project-b", schema(schemaBJson));
        assertEquals(DeploymentDecision.SAFE_TO_DEPLOY, responseB.getDecision());

        // Project A baseline should never be touched for project-b operation
        verify(contractRepository).findByProjectIdAndBaselineTrue("project-b");
        verify(contractRepository, never()).findByProjectIdAndBaselineTrue("project-a");
    }

    @Test
    void shouldAllowSameVersionInDifferentProjects() throws Exception {
        String schemaJson = "{\"type\":\"object\",\"required\":[\"name\"],\"properties\":{\"name\":{\"type\":\"string\"}}}";

        ApiContract contractA = new ApiContract();
        contractA.setProjectId("project-a");
        contractA.setVersion("1.0.0");
        contractA.setSchema(objectMapper.readTree(schemaJson));
        contractA.setBaseline(false);
        contractA.setCreatedAt(LocalDateTime.now());

        when(contractRepository.findByProjectIdAndVersion("project-a", "1.0.0"))
                .thenReturn(Optional.empty());
        when(contractRepository.findByProjectIdAndBaselineTrue("project-a"))
                .thenReturn(Optional.empty());

        // Should not throw — same version in different project is allowed
        assertDoesNotThrow(() -> service.addContract(contractA));
    }

    // ─── Invalid schema ────────────────────────────────────────────────────────

    @Test
    void shouldThrowOnInvalidSchema() throws Exception {
        JsonNode bad = schema("{\"noType\": true, \"required\": []}");
        assertThrows(IllegalArgumentException.class,
                () -> service.guardCheck("project-a", bad));
    }

    // ─── Accept (force baseline) ───────────────────────────────────────────────

    @Test
    void shouldForceAcceptSchemaAsBaseline() throws Exception {
        ApiContract existing = baseline("project-a",
                "{\"type\":\"object\",\"required\":[\"name\"],\"properties\":{\"name\":{\"type\":\"string\"}}}");
        when(contractRepository.findByProjectIdAndBaselineTrue("project-a"))
                .thenReturn(Optional.of(existing));

        JsonNode breaking = schema(
                "{\"type\":\"object\",\"required\":[],\"properties\":{}}");
        assertDoesNotThrow(() -> service.acceptContract("project-a", breaking));

        // Old baseline unset + new baseline saved = 2 saves
        verify(contractRepository, times(2)).save(any(ApiContract.class));
    }
}
