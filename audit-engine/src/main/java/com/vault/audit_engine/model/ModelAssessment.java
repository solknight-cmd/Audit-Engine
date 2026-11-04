package com.vault.audit_engine.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

/**
 * What the AI model returned for one document. This is the model's opinion only: the
 * vault's verdict is computed from it by {@code AuditVerdict}, which checks the figures itself.
 *
 * @param status the model's own verdict, expected to be PASSED or ANOMALY_DETECTED
 * @param model  the model ID that produced this, filled in by the client
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ModelAssessment(
        StatementFigures figures,
        String status,
        List<Finding> findings,
        String model
) {
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Finding(String severity, String description) {}

    public ModelAssessment withModel(String modelId) {
        return new ModelAssessment(
                figures == null ? StatementFigures.empty() : figures,
                status,
                findings == null ? List.of() : findings,
                modelId);
    }
}
