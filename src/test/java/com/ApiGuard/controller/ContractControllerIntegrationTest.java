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

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApiContractRepository repository;

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String TEST_API_KEY = "dev-secret-key";

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void shouldUploadContractSuccessfully() throws Exception {
        String body = """
                {
                  "version": "1.0.0",
                  "schema": {
                    "type": "object",
                    "required": ["name"],
                    "properties": {
                      "name": {"type": "string"}
                    }
                  }
                }
                """;

        mockMvc.perform(post("/contracts/upload")
                        .header(API_KEY_HEADER, TEST_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("1.0.0")));
    }

    @Test
    void shouldReturn409OnDuplicateVersion() throws Exception {
        String body = """
                {
                  "version": "1.0.0",
                  "schema": {
                    "type": "object",
                    "required": ["name"],
                    "properties": {"name": {"type": "string"}}
                  }
                }
                """;

        mockMvc.perform(post("/contracts/upload")
                        .header(API_KEY_HEADER, TEST_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/contracts/upload")
                        .header(API_KEY_HEADER, TEST_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldReturn401WithoutApiKey() throws Exception {
        mockMvc.perform(get("/contracts"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturn400WhenSchemaInvalid() throws Exception {
        String body = """
                {
                  "version": "1.0.0",
                  "schema": {"noType": true, "required": []}
                }
                """;

        mockMvc.perform(post("/contracts/upload")
                        .header(API_KEY_HEADER, TEST_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnAllContracts() throws Exception {
        shouldUploadContractSuccessfully();

        mockMvc.perform(get("/contracts")
                        .header(API_KEY_HEADER, TEST_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}