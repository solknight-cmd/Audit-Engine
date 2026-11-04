package com.vault.audit_engine.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static com.vault.audit_engine.service.CompanyMetricsService.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CompanyMetricsServiceTest {

    private static String statusOf(String text) {
        return determineAuditStatus(parseAssets(text), parseLiabilities(text), parseEquity(text));
    }

    @Test
    void parsesCommonStatementFormats() {
        assertEquals(new BigDecimal("100"), parseAssets("Assets: 100"));
        assertEquals(new BigDecimal("100"), parseAssets("Assets 100"));
        assertEquals(new BigDecimal("1234567.89"), parseAssets("Total Assets  $1,234,567.89"));
        assertEquals(new BigDecimal("40"), parseLiabilities("Total Liabilities = 40"));
        assertEquals(new BigDecimal("60"), parseEquity("Shareholders' Equity: $60"));
    }

    @Test
    void ignoresCombinedLiabilitiesAndEquityLine() {
        String text = "Total Liabilities: 40\nEquity: 60\nTotal Liabilities and Equity: 100";
        assertEquals(new BigDecimal("60"), parseEquity(text));
        assertNull(parseEquity("Total Liabilities and Equity 100"));
    }

    @Test
    void balancedStatementPasses() {
        assertEquals("PASSED", statusOf("Assets: 100, Liabilities: 40, Equity: 60"));
    }

    @Test
    void unbalancedStatementIsAnomaly() {
        assertEquals("ANOMALY_DETECTED", statusOf("Assets: 100, Liabilities: 40, Equity: 50"));
    }

    @Test
    void highlyLeveragedStatementIsFlagged() {
        assertEquals("HIGH_LEVERAGE_RISK", statusOf("Assets: 100, Liabilities: 90, Equity: 10"));
    }

    @Test
    void unreadableStatementIsNeverPassed() {
        assertEquals("UNABLE_TO_VERIFY", statusOf("[MULTIPART BINARY DOCUMENT: q3.pdf]"));
        assertEquals("UNABLE_TO_VERIFY", statusOf(""));
        assertEquals("UNABLE_TO_VERIFY", statusOf("Assets: 100, Liabilities: 40"));
    }
}
