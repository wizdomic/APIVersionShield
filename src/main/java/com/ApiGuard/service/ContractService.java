package com.ApiGuard.service;

import com.ApiGuard.audit.GuardAuditLog;
import com.ApiGuard.audit.GuardAuditLogRepository;
import com.ApiGuard.model.ApiContract;
import com.ApiGuard.model.DeploymentDecision;
import com.ApiGuard.model.GuardCheckResponse;
import com.ApiGuard.repository.ApiContractRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class ContractService {

    private static final Logger log = LoggerFactory.getLogger(ContractService.class);

    private final ApiContractRepository repository;
    private final GuardAuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public ContractService(ApiContractRepository repository,
                           GuardAuditLogRepository auditLogRepository,
                           ObjectMapper objectMapper) {
        this.repository = repository;
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void addContract(ApiContract contract) {
        log.info("Attempting to register contract for version: {}", contract.getVersion());
        validateSchema(contract.getSchema());

        if (repository.findByVersion(contract.getVersion()).isPresent()) {
            log.warn("Duplicate contract registration attempt for version: {}", contract.getVersion());
            throw new IllegalStateException("Contract version '" + contract.getVersion() + "' already exists");
        }

        ApiContract newContract = new ApiContract();
        newContract.setVersion(contract.getVersion());
        newContract.setSchema(contract.getSchema());
        repository.save(newContract);

        log.info("Contract registered successfully for version: {}", contract.getVersion());
    }

    @Transactional(readOnly = true)
    public Collection<ApiContract> getContracts() {
        log.debug("Fetching all contracts");
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public boolean validatePayload(String version, String payload) throws Exception {
        log.info("Validating payload against contract version: {}", version);

        ApiContract contract = repository.findByVersion(version).orElseThrow(() -> {
            log.warn("Contract not found for version: {}", version);
            return new RuntimeException("No contract found for version: " + version);
        });

        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
        JsonSchema schema = factory.getSchema(contract.getSchema());
        JsonNode payloadNode = objectMapper.readTree(payload);
        Set<ValidationMessage> errors = schema.validate(payloadNode);

        if (!errors.isEmpty()) {
            log.warn("Payload validation failed for version {}: {}", version, errors);
            throw new RuntimeException("Payload validation errors: " + errors);
        }

        log.info("Payload valid for contract version: {}", version);
        return true;
    }

    @Transactional
    public GuardCheckResponse evaluateDeployment(String from, String to) {
        log.info("Evaluating deployment compatibility: {} -> {}", from, to);

        ApiContract oldContract = repository.findByVersion(from).orElse(null);
        ApiContract newContract = repository.findByVersion(to).orElse(null);

        if (oldContract == null || newContract == null) {
            log.warn("Guard check failed - contract not found. from={} to={}", from, to);
            GuardCheckResponse response = new GuardCheckResponse(
                    DeploymentDecision.BLOCK_DEPLOYMENT,
                    "One or both contract versions do not exist"
            );
            saveAudit(from, to, response);
            return response;
        }

        List<String> breakingChanges = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        JsonNode oldProperties = oldContract.getSchema().get("properties");
        JsonNode newProperties = newContract.getSchema().get("properties");

        Set<String> oldRequired = extractRequired(oldContract.getSchema());
        Set<String> newRequired = extractRequired(newContract.getSchema());

        for (String field : oldRequired) {
            if (!newRequired.contains(field))
                breakingChanges.add("Required field removed: '" + field + "'");
        }

        for (String field : newRequired) {
            if (!oldRequired.contains(field))
                warnings.add("New required field added: '" + field + "'");
        }

        if (oldProperties != null && newProperties != null) {
            oldProperties.fieldNames().forEachRemaining(field -> {
                JsonNode oldProp = oldProperties.get(field);
                JsonNode newProp = newProperties.get(field);

                if (newProp == null) {
                    breakingChanges.add("Property removed: '" + field + "'");
                    return;
                }

                String oldType = getTextOrNull(oldProp, "type");
                String newType = getTextOrNull(newProp, "type");
                if (oldType != null && !oldType.equals(newType))
                    breakingChanges.add("Type changed for '" + field + "': " + oldType + " -> " + newType);

                String oldFormat = getTextOrNull(oldProp, "format");
                String newFormat = getTextOrNull(newProp, "format");
                if (oldFormat != null && !oldFormat.equals(newFormat))
                    breakingChanges.add("Format changed for '" + field + "': " + oldFormat + " -> " + (newFormat != null ? newFormat : "none"));
            });

            newProperties.fieldNames().forEachRemaining(field -> {
                if (oldProperties.get(field) == null)
                    warnings.add("New property added: '" + field + "'");
            });
        }

        GuardCheckResponse response;

        if (!breakingChanges.isEmpty()) {
            List<String> allChanges = new ArrayList<>(breakingChanges);
            allChanges.addAll(warnings);
            log.warn("BLOCK_DEPLOYMENT: {} -> {} | changes: {}", from, to, allChanges);
            response = new GuardCheckResponse(DeploymentDecision.BLOCK_DEPLOYMENT, "Breaking changes detected", allChanges);
        } else if (!warnings.isEmpty()) {
            log.info("WARN_ONLY: {} -> {} | warnings: {}", from, to, warnings);
            response = new GuardCheckResponse(DeploymentDecision.WARN_ONLY, "Non-breaking changes detected", warnings);
        } else {
            log.info("SAFE_TO_DEPLOY: {} -> {}", from, to);
            response = new GuardCheckResponse(DeploymentDecision.SAFE_TO_DEPLOY, "No changes detected - backward compatible", List.of());
        }

        saveAudit(from, to, response);
        return response;
    }

    @Transactional(readOnly = true)
    public List<GuardAuditLog> getAuditLog() {
        log.debug("Fetching audit log");
        return auditLogRepository.findAllOrderedByCheckedAtDesc();
    }

    private void saveAudit(String from, String to, GuardCheckResponse response) {
        auditLogRepository.save(new GuardAuditLog(from, to, response.getDecision(), response.getReason()));
    }

    private void validateSchema(JsonNode schema) {
        if (!schema.isObject()) throw new IllegalArgumentException("Schema must be a JSON object");
        if (!schema.has("type")) throw new IllegalArgumentException("Schema missing 'type'");
        if (!schema.has("required")) throw new IllegalArgumentException("Schema missing 'required'");
        if (!schema.get("required").isArray()) throw new IllegalArgumentException("'required' must be an array");
    }

    private Set<String> extractRequired(JsonNode schema) {
        Set<String> required = new HashSet<>();
        JsonNode reqNode = schema.get("required");
        if (reqNode != null && reqNode.isArray())
            for (JsonNode n : reqNode) required.add(n.asText());
        return required;
    }

    private String getTextOrNull(JsonNode node, String field) {
        JsonNode child = node.get(field);
        return (child != null && child.isTextual()) ? child.asText() : null;
    }
}