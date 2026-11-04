package com.vault.audit_engine.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.math.BigDecimal;

/**
 * Headline balance sheet figures, exactly as printed in the document (scale not applied).
 * Any figure may be null when the document doesn't state it or it couldn't be verified.
 *
 * @param scale "units", "thousands", "millions" or "billions", when the statement says so
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public record StatementFigures(
        BigDecimal totalAssets,
        BigDecimal totalLiabilities,
        BigDecimal totalEquity,
        String currency,
        String period,
        String scale
) {
    public static StatementFigures empty() {
        return new StatementFigures(null, null, null, null, null, null);
    }
}
