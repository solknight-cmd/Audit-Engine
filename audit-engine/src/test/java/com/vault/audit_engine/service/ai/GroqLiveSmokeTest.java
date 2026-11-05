package com.vault.audit_engine.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vault.audit_engine.model.AuditReport;
import com.vault.audit_engine.model.ExtractedDocument;
import com.vault.audit_engine.service.AuditVerdict;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Sends a made-up balance sheet to the real Groq API, once as text and once as an image. Skipped unless GROQ_LIVE_TEST=true,
 * so normal builds never spend quota. Run with AI_API_KEY set:
 * GROQ_LIVE_TEST=true mvn test -Dtest=GroqLiveSmokeTest
 */
@EnabledIfEnvironmentVariable(named = "GROQ_LIVE_TEST", matches = "true")
class GroqLiveSmokeTest {

    private final GroqAuditClient client = new GroqAuditClient(RestClient.builder(), new ObjectMapper(),
            "https://api.groq.com/openai/v1", System.getenv("AI_API_KEY"),
            envOr("AI_API_MODEL", "openai/gpt-oss-120b"), envOr("AI_API_VISION_MODEL", "qwen/qwen3.8-27b"));

    @Test
    void realModelExtractsVerifiableFigures() {

        ExtractedDocument document = new ExtractedDocument("smoke-test.txt", "n/a", """
                Example Ltd - Statement of financial position as at 31 December 2025 (NGN '000)
                Cash and equivalents          150,000
                Trade receivables             250,000
                Property and equipment        600,000
                Total assets                1,000,000
                Trade payables                200,000
                Long-term loans               200,000
                Total liabilities             400,000
                Share capital                 100,000
                Retained earnings             500,000
                Total equity                  600,000
                Total liabilities and equity 1,000,000
                """, List.of(), "PLAIN_TEXT", false);

        AuditReport report = AuditVerdict.buildReport(document, client.assess("BALANCE_SHEET", document));
        System.out.println("Live Groq report: " + report);

        assertEquals(List.of(), report.unverifiedFigures());
        assertEquals("PASSED", report.arithmeticStatus());
    }

    @Test
    void realVisionModelReadsAPageImage() throws IOException {
        // Draw the statement as a "scanned" page so it takes the vision path
        String[] lines = {
                "Example Ltd", "Statement of financial position as at 31 December 2025 (NGN '000)", "",
                "Total assets                    1,000,000",
                "Total liabilities                 400,000",
                "Total equity                      600,000",
                "Total liabilities and equity    1,000,000"};
        BufferedImage page = new BufferedImage(900, 360, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = page.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, page.getWidth(), page.getHeight());
        g.setColor(Color.BLACK);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 22));
        for (int i = 0; i < lines.length; i++) {
            g.drawString(lines[i], 40, 50 + i * 40);
        }
        g.dispose();
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(page, "png", png);

        ExtractedDocument document = new ExtractedDocument("smoke-test.png", "n/a", "[Image smoke-test.png]",
                List.of(new ExtractedDocument.DocumentImage("image/png", Base64.getEncoder().encodeToString(png.toByteArray()))),
                "IMAGE", false);

        AuditReport report = AuditVerdict.buildReport(document, client.assess("BALANCE_SHEET", document));
        System.out.println("Live Groq vision report: " + report);

        assertEquals("READ_FROM_IMAGES", report.figureCheck());
        assertEquals("PASSED", report.arithmeticStatus());
    }

    private static String envOr(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
