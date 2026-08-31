package com.ApiGuard.service;

//import com.ApiGuard.audit.GuardAuditLog;
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

import java.time.LocalDateTime;
import java.util.*;

@Service
public class ContractService {

  private static final Logger log = LoggerFactory.getLogger(ContractService.class);

  private final ApiContractRepository repository;
  private final GuardAuditLogRepository auditLogRepository; // kept for future use
  private final ObjectMapper objectMapper;

  public ContractService(ApiContractRepository repository,
      GuardAuditLogRepository auditLogRepository,
      ObjectMapper objectMapper) {
    this.repository = repository;
    this.auditLogRepository = auditLogRepository;
    this.objectMapper = objectMapper;
  }

  // ─── Main Guard Check ──────────────────────────────────────────────────────

  @Transactional
  public GuardCheckResponse guardCheck(String projectId, JsonNode schema) {
    log.info("Guard check for project: {}", projectId);
    validateSchema(schema);

    Optional<ApiContract> baselineOpt = repository.findByProjectIdAndBaselineTrue(projectId);

    if (baselineOpt.isEmpty()) {
      log.info("No baseline for project: {} — registering first baseline", projectId);
      ApiContract baseline = createContract(projectId, schema, true);
      repository.save(baseline);

      GuardCheckResponse response = new GuardCheckResponse(
          DeploymentDecision.SAFE_TO_DEPLOY,
          "First schema registered as baseline for project: " + projectId);
      // AUDIT TEMPORARILY DISABLED — resume when audit log is re-enabled
      // saveAudit(projectId, "none", baseline.getVersion(), response);
      return response;
    }

    ApiContract currentBaseline = baselineOpt.get();
    GuardCheckResponse response = compareSchemas(currentBaseline.getSchema(), schema);

    if (response.getDecision() == DeploymentDecision.BLOCK_DEPLOYMENT) {
      log.warn("BLOCK_DEPLOYMENT for project: {} | changes: {}", projectId, response.getChanges());
      // AUDIT TEMPORARILY DISABLED
      // saveAudit(projectId, currentBaseline.getVersion(), "rejected", response);
    } else {
      log.info("{} for project: {}", response.getDecision(), projectId);
      currentBaseline.setBaseline(false);
      repository.save(currentBaseline);

      ApiContract newBaseline = createContract(projectId, schema, true);
      repository.save(newBaseline);
      // AUDIT TEMPORARILY DISABLED
      // saveAudit(projectId, currentBaseline.getVersion(), newBaseline.getVersion(),
      // response);
    }

    return response;
  }

  // ─── Force Accept Breaking Change ──────────────────────────────────────────

  @Transactional
  public void acceptContract(String projectId, JsonNode schema) {
    log.info("Force accepting schema as new baseline for project: {}", projectId);
    validateSchema(schema);

    repository.findByProjectIdAndBaselineTrue(projectId).ifPresent(old -> {
      old.setBaseline(false);
      repository.save(old);
    });

    ApiContract newBaseline = createContract(projectId, schema, true);
    repository.save(newBaseline);
    log.info("Schema force-accepted as baseline for project: {}", projectId);
  }

  // ─── Manual Upload (backward compat) ───────────────────────────────────────

  @Transactional
  public void addContract(ApiContract contract) {
    log.info("Uploading contract for project: {} version: {}", contract.getProjectId(), contract.getVersion());
    validateSchema(contract.getSchema());

    if (repository.findByProjectIdAndVersion(contract.getProjectId(), contract.getVersion()).isPresent()) {
      log.warn("Duplicate: project={} version={}", contract.getProjectId(), contract.getVersion());
      throw new IllegalStateException(
          "Contract '" + contract.getVersion() + "' already exists for project '" + contract.getProjectId() + "'");
    }

    boolean isFirst = repository.findByProjectIdAndBaselineTrue(contract.getProjectId()).isEmpty();
    contract.setBaseline(isFirst);
    contract.setCreatedAt(LocalDateTime.now());
    if (contract.getVersion() == null || contract.getVersion().isBlank()) {
      contract.setVersion(generateVersion());
    }
    repository.save(contract);
    log.info("Contract uploaded: project={} version={}", contract.getProjectId(), contract.getVersion());
  }

  // ─── List Contracts ────────────────────────────────────────────────────────

  @Transactional(readOnly = true)
  public Collection<ApiContract> getContracts(String projectId) {
    if (projectId != null && !projectId.isBlank()) {
      return repository.findAllByProjectId(projectId);
    }
    return repository.findAll();
  }

  // ─── Payload Validation ────────────────────────────────────────────────────

  @Transactional(readOnly = true)
  public boolean validatePayload(String projectId, String version, String payload) throws Exception {
    log.info("Validating payload for project: {} version: {}", projectId, version);

    ApiContract contract;
    if (version != null && !version.isBlank()) {
      contract = repository.findByProjectIdAndVersion(projectId, version).orElseThrow(
          () -> new RuntimeException("No contract found for project: " + projectId + ", version: " + version));
    } else {
      contract = repository.findByProjectIdAndBaselineTrue(projectId)
          .orElseThrow(() -> new RuntimeException("No baseline found for project: " + projectId));
    }

    JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
    JsonSchema schema = factory.getSchema(contract.getSchema());
    JsonNode payloadNode = objectMapper.readTree(payload);
    Set<ValidationMessage> errors = schema.validate(payloadNode);

    if (!errors.isEmpty()) {
      throw new RuntimeException("Payload validation errors: " + errors);
    }
    return true;
  }

  // ─── Audit Log (TEMPORARILY DISABLED) ─────────────────────────────────────

  /*
   * @Transactional(readOnly = true)
   * public List<GuardAuditLog> getAuditLog(String projectId) {
   * if (projectId != null && !projectId.isBlank()) {
   * return auditLogRepository.findAllByProjectIdOrderByCheckedAtDesc(projectId);
   * }
   * return auditLogRepository.findAllOrderedByCheckedAtDesc();
   * }
   * 
   * private void saveAudit(String projectId, String fromVersion, String
   * toVersion, GuardCheckResponse response) {
   * auditLogRepository.save(
   * new GuardAuditLog(projectId, fromVersion, toVersion, response.getDecision(),
   * response.getReason()));
   * }
   */

  // ─── Private Helpers ───────────────────────────────────────────────────────

  private GuardCheckResponse compareSchemas(JsonNode oldSchema, JsonNode newSchema) {
    List<String> breakingChanges = new ArrayList<>();
    List<String> warnings = new ArrayList<>();

    JsonNode oldProperties = oldSchema.get("properties");
    JsonNode newProperties = newSchema.get("properties");

    Set<String> oldRequired = extractRequired(oldSchema);
    Set<String> newRequired = extractRequired(newSchema);

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
          breakingChanges.add("Format changed for '" + field + "': " + oldFormat
              + " -> " + (newFormat != null ? newFormat : "none"));
      });

      newProperties.fieldNames().forEachRemaining(field -> {
        if (oldProperties.get(field) == null)
          warnings.add("New property added: '" + field + "'");
      });
    }

    if (!breakingChanges.isEmpty()) {
      List<String> all = new ArrayList<>(breakingChanges);
      all.addAll(warnings);
      return new GuardCheckResponse(DeploymentDecision.BLOCK_DEPLOYMENT, "Breaking changes detected", all);
    } else if (!warnings.isEmpty()) {
      return new GuardCheckResponse(DeploymentDecision.WARN_ONLY, "Non-breaking changes detected", warnings);
    } else {
      return new GuardCheckResponse(DeploymentDecision.SAFE_TO_DEPLOY,
          "No changes detected - backward compatible", List.of());
    }
  }

  private ApiContract createContract(String projectId, JsonNode schema, boolean isBaseline) {
    ApiContract contract = new ApiContract();
    contract.setProjectId(projectId);
    contract.setVersion(generateVersion());
    contract.setSchema(schema);
    contract.setBaseline(isBaseline);
    contract.setCreatedAt(LocalDateTime.now());
    return contract;
  }

  private String generateVersion() {
    return "v-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
  }

  private void validateSchema(JsonNode schema) {
    if (!schema.isObject())
      throw new IllegalArgumentException("Schema must be a JSON object");
    if (!schema.has("type"))
      throw new IllegalArgumentException("Schema missing 'type'");
    if (!schema.has("required"))
      throw new IllegalArgumentException("Schema missing 'required'");
    if (!schema.get("required").isArray())
      throw new IllegalArgumentException("'required' must be an array");
  }

  private Set<String> extractRequired(JsonNode schema) {
    Set<String> required = new HashSet<>();
    JsonNode reqNode = schema.get("required");
    if (reqNode != null && reqNode.isArray())
      for (JsonNode n : reqNode)
        required.add(n.asText());
    return required;
  }

  private String getTextOrNull(JsonNode node, String field) {
    JsonNode child = node.get(field);
    return (child != null && child.isTextual()) ? child.asText() : null;
  }
}
