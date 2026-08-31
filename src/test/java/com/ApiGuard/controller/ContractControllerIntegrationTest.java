package com.ApiGuard.controller;

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
class ContractControllerIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ApiContractRepository repository;

    private static final String KEY = "X-API-Key";
    private static final String API_KEY = "dev-secret-key";

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    private String uploadBody(String projectId, String version, String schemaJson) {
        return String.format(
                "{\"projectId\":\"%s\",\"version\":\"%s\",\"schema\":%s}",
                projectId, version, schemaJson);
    }

    // ─── Upload ────────────────────────────────────────────────────────────────

    @Test
    void shouldUploadContractSuccessfully() throws Exception {
        mockMvc.perform(post("/contracts/upload")
                        .header(KEY, API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(uploadBody("project-a", "1.0.0",
                                "{\"type\":\"object\",\"required\":[\"name\"],\"properties\":{\"name\":{\"type\":\"string\"}}}")))
                .andExpect(status().isCreated())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("project-a")));
    }

    @Test
    void shouldReturn409OnDuplicateProjectAndVersion() throws Exception {
        String body = uploadBody("project-a", "1.0.0",
                "{\"type\":\"object\",\"required\":[\"name\"],\"properties\":{\"name\":{\"type\":\"string\"}}}");

        mockMvc.perform(post("/contracts/upload")
                        .header(KEY, API_KEY).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/contracts/upload")
                        .header(KEY, API_KEY).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldAllowSameVersionInDifferentProjects() throws Exception {
        String schema = "{\"type\":\"object\",\"required\":[\"name\"],\"properties\":{\"name\":{\"type\":\"string\"}}}";

        // project-a / 1.0.0
        mockMvc.perform(post("/contracts/upload")
                        .header(KEY, API_KEY).contentType(MediaType.APPLICATION_JSON)
                        .content(uploadBody("project-a", "1.0.0", schema)))
                .andExpect(status().isCreated());

        // project-b / 1.0.0 — same version, different project → allowed
        mockMvc.perform(post("/contracts/upload")
                        .header(KEY, API_KEY).contentType(MediaType.APPLICATION_JSON)
                        .content(uploadBody("project-b", "1.0.0", schema)))
                .andExpect(status().isCreated());
    }

    @Test
    void shouldReturn400WhenSchemaInvalid() throws Exception {
        mockMvc.perform(post("/contracts/upload")
                        .header(KEY, API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(uploadBody("project-a", "1.0.0",
                                "{\"noType\":true,\"required\":[]}")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn401WithoutApiKey() throws Exception {
        mockMvc.perform(get("/contracts"))
                .andExpect(status().isUnauthorized());
    }

    // ─── List ──────────────────────────────────────────────────────────────────

    @Test
    void shouldReturnAllContracts() throws Exception {
        String schema = "{\"type\":\"object\",\"required\":[\"name\"],\"properties\":{\"name\":{\"type\":\"string\"}}}";
        mockMvc.perform(post("/contracts/upload")
                        .header(KEY, API_KEY).contentType(MediaType.APPLICATION_JSON)
                        .content(uploadBody("project-a", "1.0.0", schema)));

        mockMvc.perform(get("/contracts").header(KEY, API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void shouldFilterContractsByProjectId() throws Exception {
        String schema = "{\"type\":\"object\",\"required\":[\"name\"],\"properties\":{\"name\":{\"type\":\"string\"}}}";

        mockMvc.perform(post("/contracts/upload").header(KEY, API_KEY)
                .contentType(MediaType.APPLICATION_JSON).content(uploadBody("project-a", "1.0.0", schema)));
        mockMvc.perform(post("/contracts/upload").header(KEY, API_KEY)
                .contentType(MediaType.APPLICATION_JSON).content(uploadBody("project-b", "1.0.0", schema)));

        mockMvc.perform(get("/contracts?projectId=project-a").header(KEY, API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].projectId").value("project-a"));
    }

    // ─── Accept ────────────────────────────────────────────────────────────────

    @Test
    void shouldAcceptBreakingSchemaAsBaseline() throws Exception {
        String accept = "{\"projectId\":\"project-a\",\"schema\":{\"type\":\"object\",\"required\":[],\"properties\":{}}}";

        mockMvc.perform(post("/contracts/accept")
                        .header(KEY, API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accept))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("project-a")));
    }
}
