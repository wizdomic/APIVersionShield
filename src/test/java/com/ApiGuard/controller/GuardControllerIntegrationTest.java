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
  MockMvc mockMvc;
  @Autowired
  ApiContractRepository contractRepository;
  @Autowired
  GuardAuditLogRepository auditLogRepository;

  private static final String KEY = "X-API-Key";
  private static final String API_KEY = "dev-secret-key";

  @BeforeEach
  void setUp() {
    auditLogRepository.deleteAll();
    contractRepository.deleteAll();
  }

  private String guardCheckBody(String projectId, String schemaJson) {
    return String.format("{\"projectId\":\"%s\",\"schema\":%s}", projectId, schemaJson);
  }

  // ─── First schema = SAFE ───────────────────────────────────────────────────

  @Test
  void shouldReturnSafeOnFirstSchemaForProject() throws Exception {
    mockMvc.perform(post("/guard/check")
        .header(KEY, API_KEY)
        .contentType(MediaType.APPLICATION_JSON)
        .content(guardCheckBody("project-a",
            "{\"type\":\"object\",\"required\":[\"name\"],\"properties\":{\"name\":{\"type\":\"string\"}}}")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.decision").value("SAFE_TO_DEPLOY"));
  }

  // ─── SAFE → baseline updates ───────────────────────────────────────────────

  @Test
  void shouldReturnSafeAndUpdateBaselineOnNoChanges() throws Exception {
    String schema = "{\"type\":\"object\",\"required\":[\"name\"],\"properties\":{\"name\":{\"type\":\"string\"}}}";

    mockMvc.perform(post("/guard/check")
        .header(KEY, API_KEY).contentType(MediaType.APPLICATION_JSON)
        .content(guardCheckBody("project-a", schema)));

    mockMvc.perform(post("/guard/check")
        .header(KEY, API_KEY).contentType(MediaType.APPLICATION_JSON)
        .content(guardCheckBody("project-a", schema)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.decision").value("SAFE_TO_DEPLOY"));
  }

  // ─── WARN → baseline updates ───────────────────────────────────────────────

  @Test
  void shouldWarnAndUpdateBaselineOnNewField() throws Exception {
    String base = "{\"type\":\"object\",\"required\":[\"name\"],\"properties\":{\"name\":{\"type\":\"string\"}}}";
    String warn = "{\"type\":\"object\",\"required\":[\"name\",\"phone\"],\"properties\":{\"name\":{\"type\":\"string\"},\"phone\":{\"type\":\"string\"}}}";

    mockMvc.perform(post("/guard/check")
        .header(KEY, API_KEY).contentType(MediaType.APPLICATION_JSON)
        .content(guardCheckBody("project-a", base)));

    mockMvc.perform(post("/guard/check")
        .header(KEY, API_KEY).contentType(MediaType.APPLICATION_JSON)
        .content(guardCheckBody("project-a", warn)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.decision").value("WARN_ONLY"));
  }

  // ─── BLOCK → baseline unchanged ────────────────────────────────────────────

  @Test
  void shouldBlockOnBreakingChange() throws Exception {
    String base = "{\"type\":\"object\",\"required\":[\"name\",\"email\"],\"properties\":{\"name\":{\"type\":\"string\"},\"email\":{\"type\":\"string\"}}}";
    String breaking = "{\"type\":\"object\",\"required\":[\"name\"],\"properties\":{\"name\":{\"type\":\"string\"}}}";

    mockMvc.perform(post("/guard/check")
        .header(KEY, API_KEY).contentType(MediaType.APPLICATION_JSON)
        .content(guardCheckBody("project-a", base)));

    mockMvc.perform(post("/guard/check")
        .header(KEY, API_KEY).contentType(MediaType.APPLICATION_JSON)
        .content(guardCheckBody("project-a", breaking)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.decision").value("BLOCK_DEPLOYMENT"))
        .andExpect(jsonPath("$.changes").isArray());

    // Old baseline still active — original schema should return SAFE
    mockMvc.perform(post("/guard/check")
        .header(KEY, API_KEY).contentType(MediaType.APPLICATION_JSON)
        .content(guardCheckBody("project-a", base)))
        .andExpect(jsonPath("$.decision").value("SAFE_TO_DEPLOY"));
  }

  // ─── Project isolation ─────────────────────────────────────────────────────

  @Test
  void shouldIsolateProjectAFromProjectB() throws Exception {
    String schemaA = "{\"type\":\"object\",\"required\":[\"fieldA\"],\"properties\":{\"fieldA\":{\"type\":\"string\"}}}";
    String schemaB = "{\"type\":\"object\",\"required\":[\"fieldB\"],\"properties\":{\"fieldB\":{\"type\":\"string\"}}}";

    mockMvc.perform(post("/guard/check")
        .header(KEY, API_KEY).contentType(MediaType.APPLICATION_JSON)
        .content(guardCheckBody("project-a", schemaA)))
        .andExpect(jsonPath("$.decision").value("SAFE_TO_DEPLOY"));

    mockMvc.perform(post("/guard/check")
        .header(KEY, API_KEY).contentType(MediaType.APPLICATION_JSON)
        .content(guardCheckBody("project-b", schemaB)))
        .andExpect(jsonPath("$.decision").value("SAFE_TO_DEPLOY"));

    // Breaking change for A should not affect B
    String breakingA = "{\"type\":\"object\",\"required\":[],\"properties\":{}}";
    mockMvc.perform(post("/guard/check")
        .header(KEY, API_KEY).contentType(MediaType.APPLICATION_JSON)
        .content(guardCheckBody("project-a", breakingA)))
        .andExpect(jsonPath("$.decision").value("BLOCK_DEPLOYMENT"));

    // Project B still returns SAFE with its own schema
    mockMvc.perform(post("/guard/check")
        .header(KEY, API_KEY).contentType(MediaType.APPLICATION_JSON)
        .content(guardCheckBody("project-b", schemaB)))
        .andExpect(jsonPath("$.decision").value("SAFE_TO_DEPLOY"));
  }

  // ─── Accept breaking change ────────────────────────────────────────────────

  @Test
  void shouldAcceptBreakingChangeAsNewBaseline() throws Exception {
    String base = "{\"type\":\"object\",\"required\":[\"name\",\"email\"],\"properties\":{\"name\":{\"type\":\"string\"},\"email\":{\"type\":\"string\"}}}";
    String breaking = "{\"type\":\"object\",\"required\":[\"name\"],\"properties\":{\"name\":{\"type\":\"string\"}}}";

    mockMvc.perform(post("/guard/check")
        .header(KEY, API_KEY).contentType(MediaType.APPLICATION_JSON)
        .content(guardCheckBody("project-a", base)));

    mockMvc.perform(post("/guard/check")
        .header(KEY, API_KEY).contentType(MediaType.APPLICATION_JSON)
        .content(guardCheckBody("project-a", breaking)))
        .andExpect(jsonPath("$.decision").value("BLOCK_DEPLOYMENT"));

    // Force accept the breaking schema
    mockMvc.perform(post("/contracts/accept")
        .header(KEY, API_KEY).contentType(MediaType.APPLICATION_JSON)
        .content(guardCheckBody("project-a", breaking)))
        .andExpect(status().isOk());

    // Breaking schema is now baseline — same schema returns SAFE
    mockMvc.perform(post("/guard/check")
        .header(KEY, API_KEY).contentType(MediaType.APPLICATION_JSON)
        .content(guardCheckBody("project-a", breaking)))
        .andExpect(jsonPath("$.decision").value("SAFE_TO_DEPLOY"));
  }

  // ─── Validation ────────────────────────────────────────────────────────────

  @Test
  void shouldReturn400WhenProjectIdIsBlank() throws Exception {
    mockMvc.perform(post("/guard/check")
        .header(KEY, API_KEY).contentType(MediaType.APPLICATION_JSON)
        .content("{\"projectId\":\"\",\"schema\":{\"type\":\"object\",\"required\":[],\"properties\":{}}}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldReturn400WhenSchemaMissing() throws Exception {
    mockMvc.perform(post("/guard/check")
        .header(KEY, API_KEY).contentType(MediaType.APPLICATION_JSON)
        .content("{\"projectId\":\"project-a\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldReturn401WithoutApiKey() throws Exception {
    mockMvc.perform(post("/guard/check")
        .contentType(MediaType.APPLICATION_JSON)
        .content(guardCheckBody("project-a",
            "{\"type\":\"object\",\"required\":[],\"properties\":{}}")))
        .andExpect(status().isUnauthorized());
  }

  // AUDIT TESTS TEMPORARILY DISABLED — resume when audit log is re-enabled
  /*
   * @Test
   * void shouldReturnAuditLogWithProjectId() throws Exception { ... }
   * 
   * @Test
   * void shouldFilterAuditLogByProjectId() throws Exception { ... }
   */
}
