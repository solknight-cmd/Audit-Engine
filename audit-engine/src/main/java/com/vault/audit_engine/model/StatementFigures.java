package com.vault.audit_engine.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Amounts read from a statement, exactly as printed (scale not applied). Which amounts are present
 * depends on the {@link DocumentType}, e.g. total_assets for a balance sheet or revenue for an
 * income statement. An amount is null when the document doesn't state it or it couldn't be verified.
 *
 * @param scale "units", "thousands", "millions" or "billions", when the statement says so
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public record StatementFigures(
        @JsonDeserialize(contentUsing = LenientAmountDeserializer.class) Map<String, BigDecimal> amounts,
        String currency,
        String period,
        String scale
) {
    public StatementFigures {
        // Keep the model's order for display; LinkedHashMap also allows null values
        amounts = amounts == null ? new LinkedHashMap<>() : new LinkedHashMap<>(amounts);
    }

    public static StatementFigures empty() {
        return new StatementFigures(null, null, null, null);
    }

    public BigDecimal amount(String name) {
        return amounts.get(name);
    }

    public StatementFigures withAmounts(Map<String, BigDecimal> newAmounts) {
        return new StatementFigures(newAmounts, currency, period, scale);
    }
}
