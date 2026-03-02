package com.ApiGuard.controller;

import com.ApiGuard.audit.GuardAuditLogRepository;
import com.ApiGuard.repository.ApiContractRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GuardControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApiContractRepository contractRepository;

    @Autowired
    private GuardAuditLogRepository auditLogRepository;

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String TEST_API_KEY = "dev-secret-key";

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        contractRepository.deleteAll();
    }

    private void uploadContract(String version, String schemaJson) throws Exception {
        String body = String.format("""
                {"version": "%s", "schema": %s}
                """, version, schemaJson);

        mockMvc.perform(post("/contracts/upload")
                .header(API_KEY_HEADER, TEST_API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    void shouldReturnSafeToDeployWhenNoChanges() throws Exception {
        String schema = """
                {"type":"object","required":["name"],"properties":{"name":{"type":"string"}}}
                """;
        uploadContract("1.0.0", schema);
        uploadContract("2.0.0", schema);

        mockMvc.perform(post("/guard/check")
                        .header(API_KEY_HEADER, TEST_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"from": "1.0.0", "to": "2.0.0"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("SAFE_TO_DEPLOY"));
    }

    @Test
    void shouldBlockWhenRequiredFieldRemoved() throws Exception {
        uploadContract("1.0.0", """
                {"type":"object","required":["name","email"],"properties":{"name":{"type":"string"},"email":{"type":"string"}}}
                """);
        uploadContract("2.0.0", """
                {"type":"object","required":["name"],"properties":{"name":{"type":"string"}}}
                """);

        mockMvc.perform(post("/guard/check")
                        .header(API_KEY_HEADER, TEST_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"from": "1.0.0", "to": "2.0.0"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("BLOCK_DEPLOYMENT"))
                .andExpect(jsonPath("$.changes").isArray());
    }

    @Test
    void shouldReturnWarnOnlyForNewField() throws Exception {
        uploadContract("1.0.0", """
                {"type":"object","required":["name"],"properties":{"name":{"type":"string"}}}
                """);
        uploadContract("2.0.0", """
                {"type":"object","required":["name","phone"],"properties":{"name":{"type":"string"},"phone":{"type":"string"}}}
                """);

        mockMvc.perform(post("/guard/check")
                        .header(API_KEY_HEADER, TEST_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"from": "1.0.0", "to": "2.0.0"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("WARN_ONLY"));
    }

    @Test
    void shouldReturn400WhenFromIsBlank() throws Exception {
        mockMvc.perform(post("/guard/check")
                        .header(API_KEY_HEADER, TEST_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"from": null, "to": "2.0.0"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnAuditLog() throws Exception {
        shouldReturnSafeToDeployWhenNoChanges();

        mockMvc.perform(get("/guard/audit")
                        .header(API_KEY_HEADER, TEST_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].decision").exists());
    }
}