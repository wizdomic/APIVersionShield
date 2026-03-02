package com.ApiGuard.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GuardCheckRequest {

    @NotBlank(message = "'from' version must not be blank")
    private String from;

    @NotBlank(message = "'to' version must not be blank")
    private String to;
}