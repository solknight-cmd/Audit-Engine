package com.vault.audit_engine.model;

import java.util.List;
import java.util.Locale;

/**
 * The kinds of statement the engine knows how to check, with the amounts the AI model is asked
 * to read for each. The checks on those amounts live in {@code StatementChecks}.
 */
public enum DocumentType {

    BALANCE_SHEET(
            List.of("total_assets", "total_liabilities", "total_equity"),
            "total_equity is total shareholders' or owners' equity, not \"total liabilities and equity\"."),
    INCOME_STATEMENT(
            List.of("revenue", "cost_of_sales", "gross_profit", "profit_before_tax", "income_tax", "net_profit"),
            "net_profit is the profit or loss for the period after tax. A loss is negative."),
    CASH_FLOW_STATEMENT(
            List.of("net_cash_from_operating", "net_cash_from_investing", "net_cash_from_financing",
                    "net_change_in_cash", "opening_cash", "closing_cash"),
            "Cash outflows and a net decrease in cash are negative. opening_cash and closing_cash are the "
                    + "cash and cash equivalents at the start and end of the period."),
    TRIAL_BALANCE(
            List.of("total_debits", "total_credits"),
            "total_debits and total_credits are the totals of the debit and credit columns."),
    OTHER(List.of(), "");

    private final List<String> amountFields;
    private final String guidance;

    DocumentType(List<String> amountFields, String guidance) {
        this.amountFields = amountFields;
        this.guidance = guidance;
    }

    /** The amounts the AI model is asked to report for this kind of document. */
    public List<String> amountFields() {
        return amountFields;
    }

    /** Extra instructions for reading this kind of document. */
    public String guidance() {
        return guidance;
    }

    /** Accepts "BALANCE_SHEET", "Balance Sheet", "balance-sheet"; anything unknown is OTHER. */
    public static DocumentType from(String value) {
        if (value == null) {
            return OTHER;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT).replaceAll("[\\s-]+", "_");
        for (DocumentType type : values()) {
            if (type.name().equals(normalized)) {
                return type;
            }
        }
        return OTHER;
    }
}
