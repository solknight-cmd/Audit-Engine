package com.vault.audit_engine.service;

import com.vault.audit_engine.model.AuditReport.CheckResult;
import com.vault.audit_engine.model.DocumentType;
import com.vault.audit_engine.model.StatementFigures;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static com.vault.audit_engine.service.AuditVerdict.*;

/**
 * The server's own arithmetic checks, one set per document type. A check only runs when every amount
 * it needs was found in the document; if no check can run the result is UNABLE_TO_VERIFY, never PASSED.
 */
public final class StatementChecks {

    public record Outcome(String status, List<CheckResult> checks) {}

    private static final BigDecimal HIGH_LEVERAGE_RATIO = new BigDecimal("0.80");

    private StatementChecks() {}

    public static Outcome run(DocumentType type, StatementFigures figures) {
        List<CheckResult> checks = new ArrayList<>();
        boolean highLeverage = false;

        switch (type) {
            case BALANCE_SHEET -> {
                BigDecimal assets = figures.amount("total_assets");
                BigDecimal liabilities = figures.amount("total_liabilities");
                BigDecimal equity = figures.amount("total_equity");
                addEquation(checks, "Assets = Liabilities + Equity", assets, sum(liabilities, equity), 3);

                if (assets != null && liabilities != null && assets.signum() > 0) {
                    boolean withinLimit = liabilities.compareTo(assets.multiply(HIGH_LEVERAGE_RATIO)) <= 0;
                    checks.add(new CheckResult("Liabilities at most 80% of assets", withinLimit, null));
                    highLeverage = !withinLimit;
                }
            }
            case INCOME_STATEMENT -> {
                BigDecimal costOfSales = abs(figures.amount("cost_of_sales"));
                addEquation(checks, "Revenue − Cost of sales = Gross profit",
                        figures.amount("gross_profit"), difference(figures.amount("revenue"), costOfSales), 3);
                addProfitAfterTax(checks, figures);
            }
            case CASH_FLOW_STATEMENT -> {
                BigDecimal netChange = figures.amount("net_change_in_cash");
                addEquation(checks, "Opening cash + Net change = Closing cash",
                        figures.amount("closing_cash"), sum(figures.amount("opening_cash"), netChange), 3);
                addEquation(checks, "Operating + Investing + Financing = Net change in cash", netChange,
                        sum(figures.amount("net_cash_from_operating"), figures.amount("net_cash_from_investing"),
                                figures.amount("net_cash_from_financing")), 4);
            }
            case TRIAL_BALANCE -> addEquation(checks, "Total debits = Total credits",
                    figures.amount("total_debits"), figures.amount("total_credits"), 2);
            case OTHER -> {
                // No standard arithmetic for unknown document types
            }
        }

        String status;
        if (checks.stream().anyMatch(c -> !c.passed() && c.difference() != null)) {
            status = ANOMALY_DETECTED;
        } else if (checks.stream().noneMatch(c -> c.difference() != null)) {
            // Only equations verify a statement; the leverage ratio alone can't make it pass
            status = UNABLE_TO_VERIFY;
        } else if (highLeverage) {
            status = HIGH_LEVERAGE_RISK;
        } else {
            status = PASSED;
        }
        return new Outcome(status, checks);
    }

    /** Tax can be a charge or a credit, and is printed either way round, so either sign is accepted. */
    private static void addProfitAfterTax(List<CheckResult> checks, StatementFigures figures) {
        BigDecimal beforeTax = figures.amount("profit_before_tax");
        BigDecimal tax = abs(figures.amount("income_tax"));
        BigDecimal net = figures.amount("net_profit");
        if (beforeTax == null || tax == null || net == null) {
            return;
        }
        BigDecimal asCharge = net.subtract(beforeTax.subtract(tax));
        BigDecimal asCredit = net.subtract(beforeTax.add(tax));
        BigDecimal closest = asCharge.abs().compareTo(asCredit.abs()) <= 0 ? asCharge : asCredit;
        checks.add(new CheckResult("Profit before tax − Tax = Net profit", withinTolerance(closest, 3), closest));
    }

    /**
     * Adds "left = right" when both sides are known. Each printed amount can be off by up to half a unit
     * from rounding, so the tolerance grows with the number of amounts in the equation.
     */
    private static void addEquation(List<CheckResult> checks, String name, BigDecimal left, BigDecimal right, int amountsInvolved) {
        if (left == null || right == null) {
            return;
        }
        BigDecimal difference = left.subtract(right);
        checks.add(new CheckResult(name, withinTolerance(difference, amountsInvolved), difference));
    }

    private static boolean withinTolerance(BigDecimal difference, int amountsInvolved) {
        BigDecimal tolerance = new BigDecimal(Math.max(1, amountsInvolved - 2));
        return difference.abs().compareTo(tolerance) <= 0;
    }

    private static BigDecimal sum(BigDecimal... values) {
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal value : values) {
            if (value == null) {
                return null;
            }
            total = total.add(value);
        }
        return total;
    }

    private static BigDecimal difference(BigDecimal a, BigDecimal b) {
        return a == null || b == null ? null : a.subtract(b);
    }

    private static BigDecimal abs(BigDecimal value) {
        return value == null ? null : value.abs();
    }
}
