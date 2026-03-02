package com.ApiGuard.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "api_contracts")
@Getter
@Setter
@NoArgsConstructor
public class ApiContract {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Version must not be blank")
    @Column(unique = true, nullable = false)
    private String version;

    @NotNull(message = "Schema must not be null")
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private JsonNode schema;
}