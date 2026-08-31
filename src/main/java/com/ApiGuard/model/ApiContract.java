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

import java.time.LocalDateTime;

@Entity
@Table(name = "api_contracts", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"project_id", "version"})
})
@Getter
@Setter
@NoArgsConstructor
public class ApiContract {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "projectId must not be blank")
    @Column(name = "project_id", nullable = false)
    private String projectId;

    @Column(nullable = false)
    private String version;

    @NotNull(message = "Schema must not be null")
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private JsonNode schema;

    @Column(nullable = false)
    private boolean baseline;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
