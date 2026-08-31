package com.ApiGuard.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GuardCheckRequest {

    @NotBlank(message = "projectId must not be blank")
    private String projectId;

    @NotNull(message = "schema must not be null")
    private JsonNode schema;
}
