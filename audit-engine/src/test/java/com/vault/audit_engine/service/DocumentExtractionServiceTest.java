package com.vault.audit_engine.service;

import com.vault.audit_engine.model.ExtractedDocument;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class DocumentExtractionServiceTest {

    private final DocumentExtractionService service = new DocumentExtractionService(30000, 3);

    @Test
    void textPdfIsReadAsText() throws IOException {
        ExtractedDocument doc = service.extract(file("Q4.PDF", pdf(
                "Balance sheet as at 31 December 2025", "Total assets 1,250,000", "Total liabilities 500,000")));

        assertEquals("PDF_TEXT", doc.method());
        assertTrue(doc.text().contains("Total assets 1,250,000"));
        assertFalse(doc.hasImages());
        assertEquals(64, doc.sha256().length());
    }

    @Test
    void pdfWithoutTextIsSentAsPageImages() throws IOException {
        ExtractedDocument doc = service.extract(file("scan.pdf", pdf()));

        assertEquals("PDF_SCANNED", doc.method());
        assertEquals(1, doc.images().size());
        assertEquals("image/jpeg", doc.images().get(0).mediaType());
    }

    @Test
    void spreadsheetNumbersAreNotInScientificNotation() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            var row = workbook.createSheet("BS").createRow(0);
            row.createCell(0).setCellValue("Total assets");
            row.createCell(1).setCellValue(12_500_000_000d);
            workbook.write(out);
        }
        ExtractedDocument doc = service.extract(file("bs.xlsx", out.toByteArray()));

        assertTrue(doc.text().contains("12500000000"), doc.text());
        assertFalse(doc.text().contains("E10"));
    }

    @Test
    void longTextIsTruncatedAndFlagged() {
        DocumentExtractionService small = new DocumentExtractionService(10, 3);
        ExtractedDocument doc = small.extract(file("long.txt", "x".repeat(50).getBytes(StandardCharsets.UTF_8)));

        assertEquals(10, doc.text().length());
        assertTrue(doc.truncated());
    }

    @Test
    void unsupportedAndBrokenFilesAreRejected() {
        assertThrows(DocumentRejectedException.class, () -> service.extract(file("report.docx", new byte[]{1})));
        assertThrows(DocumentRejectedException.class, () -> service.extract(file("bad.pdf", "not a pdf".getBytes())));
        assertThrows(DocumentRejectedException.class, () -> service.extract(file("empty.csv", new byte[0])));
    }

    private static MockMultipartFile file(String name, byte[] content) {
        return new MockMultipartFile("file", name, null, content);
    }

    private static byte[] pdf(String... lines) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            if (lines.length > 0) {
                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                    stream.beginText();
                    stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    stream.newLineAtOffset(50, 700);
                    for (String line : lines) {
                        stream.showText(line);
                        stream.newLineAtOffset(0, -20);
                    }
                    stream.endText();
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }
}
