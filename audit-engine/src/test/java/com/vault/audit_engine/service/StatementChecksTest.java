package com.vault.audit_engine.service;

import com.vault.audit_engine.model.AuditReport.CheckResult;
import com.vault.audit_engine.model.DocumentType;
import com.vault.audit_engine.model.StatementFigures;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.vault.audit_engine.service.AuditVerdict.*;
import static org.junit.jupiter.api.Assertions.*;

class StatementChecksTest {

    /** figures("revenue", "500", "cost_of_sales", "-300", ...); "null" leaves an amount unverified. */
    private static StatementFigures figures(String... namesAndValues) {
        Map<String, BigDecimal> amounts = new LinkedHashMap<>();
        for (int i = 0; i < namesAndValues.length; i += 2) {
            String value = namesAndValues[i + 1];
            amounts.put(namesAndValues[i], value.equals("null") ? null : new BigDecimal(value));
        }
        return new StatementFigures(amounts, "NGN", null, null);
    }

    private static StatementChecks.Outcome run(DocumentType type, String... namesAndValues) {
        return StatementChecks.run(type, figures(namesAndValues));
    }

    @Test
    void balanceSheet() {
        assertEquals(PASSED, run(DocumentType.BALANCE_SHEET,
                "total_assets", "100", "total_liabilities", "40", "total_equity", "60").status());
        assertEquals(ANOMALY_DETECTED, run(DocumentType.BALANCE_SHEET,
                "total_assets", "100", "total_liabilities", "40", "total_equity", "50").status());
        assertEquals(HIGH_LEVERAGE_RISK, run(DocumentType.BALANCE_SHEET,
                "total_assets", "100", "total_liabilities", "90", "total_equity", "10").status());
        assertEquals(UNABLE_TO_VERIFY, run(DocumentType.BALANCE_SHEET,
                "total_assets", "100", "total_liabilities", "null", "total_equity", "60").status());
    }

    @Test
    void incomeStatementPassesWhetherCostsArePrintedPositiveOrInParentheses() {
        assertEquals(PASSED, run(DocumentType.INCOME_STATEMENT,
                "revenue", "5000", "cost_of_sales", "-3000", "gross_profit", "2000",
                "profit_before_tax", "800", "income_tax", "-240", "net_profit", "560").status());
        assertEquals(PASSED, run(DocumentType.INCOME_STATEMENT,
                "revenue", "5000", "cost_of_sales", "3000", "gross_profit", "2000",
                "profit_before_tax", "800", "income_tax", "240", "net_profit", "560").status());
    }

    @Test
    void incomeStatementWithWrongGrossProfitIsAnomaly() {
        StatementChecks.Outcome outcome = run(DocumentType.INCOME_STATEMENT,
                "revenue", "5000", "cost_of_sales", "3000", "gross_profit", "2500");
        assertEquals(ANOMALY_DETECTED, outcome.status());
        CheckResult check = outcome.checks().get(0);
        assertEquals("Revenue − Cost of sales = Gross profit", check.name());
        assertEquals(new BigDecimal("500"), check.difference());
    }

    @Test
    void lossWithTaxCreditIsAccepted() {
        // Loss before tax of 100, tax credit of 20, loss after tax of 80
        assertEquals(PASSED, run(DocumentType.INCOME_STATEMENT,
                "profit_before_tax", "-100", "income_tax", "20", "net_profit", "-80").status());
    }

    @Test
    void incomeStatementWithNoCompleteCheckIsUnableToVerify() {
        assertEquals(UNABLE_TO_VERIFY, run(DocumentType.INCOME_STATEMENT,
                "revenue", "5000", "net_profit", "560").status());
    }

    @Test
    void cashFlowStatement() {
        assertEquals(PASSED, run(DocumentType.CASH_FLOW_STATEMENT,
                "net_cash_from_operating", "900", "net_cash_from_investing", "-600", "net_cash_from_financing", "-200",
                "net_change_in_cash", "100", "opening_cash", "400", "closing_cash", "500").status());
        // Closing cash doesn't follow from opening cash and the net change
        assertEquals(ANOMALY_DETECTED, run(DocumentType.CASH_FLOW_STATEMENT,
                "net_change_in_cash", "-100", "opening_cash", "400", "closing_cash", "500").status());
    }

    @Test
    void roundingDifferencesAreTolerated() {
        // Four rounded amounts can be off by up to 2
        assertEquals(PASSED, run(DocumentType.CASH_FLOW_STATEMENT,
                "net_cash_from_operating", "901", "net_cash_from_investing", "-600", "net_cash_from_financing", "-199",
                "net_change_in_cash", "100").status());
        assertEquals(ANOMALY_DETECTED, run(DocumentType.CASH_FLOW_STATEMENT,
                "net_cash_from_operating", "905", "net_cash_from_investing", "-600", "net_cash_from_financing", "-200",
                "net_change_in_cash", "100").status());
    }

    @Test
    void trialBalance() {
        assertEquals(PASSED, run(DocumentType.TRIAL_BALANCE, "total_debits", "12500", "total_credits", "12500").status());
        assertEquals(ANOMALY_DETECTED, run(DocumentType.TRIAL_BALANCE, "total_debits", "12500", "total_credits", "12400").status());
    }

    @Test
    void unknownDocumentTypeIsNeverPassedOnArithmetic() {
        assertEquals(UNABLE_TO_VERIFY, run(DocumentType.OTHER).status());
        assertEquals(DocumentType.OTHER, DocumentType.from("board minutes"));
        assertEquals(DocumentType.INCOME_STATEMENT, DocumentType.from("Income Statement"));
    }
}
