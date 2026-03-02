package com.ApiGuard;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@OpenAPIDefinition(
		info = @Info(title = "APIGuard", version = "1.0", description = "API Contract Validation & Deployment Gate"),
		security = @SecurityRequirement(name = "X-API-Key")
)
@SecurityScheme(
		name = "X-API-Key",
		type = SecuritySchemeType.APIKEY,
		in = SecuritySchemeIn.HEADER,
		paramName = "X-API-Key"
)
public class ApiGuardApplication {
	public static void main(String[] args) {
		SpringApplication.run(ApiGuardApplication.class, args);
	}
}