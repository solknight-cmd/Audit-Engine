package com.vault.audit_engine.service;

import com.vault.audit_engine.model.ExtractedDocument;
import com.vault.audit_engine.model.ExtractedDocument.DocumentImage;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

@Service
public class DocumentExtractionService {

    // A PDF with less text than this is treated as scanned and sent as page images
    private static final int MIN_PDF_TEXT_CHARS = 50;
    private static final float RENDER_DPI = 150;
    private static final long MAX_IMAGE_BYTES = 4 * 1024 * 1024;

    private final int maxInputChars;
    private final int maxImages;

    public DocumentExtractionService(@Value("${ai.max-input-chars:30000}") int maxInputChars,
                                     @Value("${ai.max-images:3}") int maxImages) {
        this.maxInputChars = maxInputChars;
        this.maxImages = maxImages;
    }

    public ExtractedDocument extract(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            throw new DocumentRejectedException("Cannot process a file without a valid name.");
        }
        if (file.isEmpty()) {
            throw new DocumentRejectedException("Uploaded file cannot be empty.");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new DocumentRejectedException("Could not read the uploaded file.", e);
        }
        String sha256 = sha256(bytes);

        String extension = filename.contains(".")
                ? filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT)
                : "";

        try {
            return switch (extension) {
                case "xlsx", "xls" -> textDocument(filename, sha256, extractSpreadsheetText(bytes), "SPREADSHEET");
                case "txt", "csv" -> textDocument(filename, sha256, new String(bytes, StandardCharsets.UTF_8), "PLAIN_TEXT");
                case "pdf" -> extractPdf(filename, sha256, bytes);
                case "png" -> imageDocument(filename, sha256, bytes, "image/png");
                case "jpg", "jpeg" -> imageDocument(filename, sha256, bytes, "image/jpeg");
                default -> throw new DocumentRejectedException(
                        "Unsupported file format: " + filename + ". Use PDF, Excel, CSV, TXT, PNG or JPG.");
            };
        } catch (IOException e) {
            throw new DocumentRejectedException("Could not read " + filename + ". Is the file corrupted?", e);
        }
    }

    /** Statements submitted as raw text through the API rather than as a file. */
    public ExtractedDocument fromText(String rawText) {
        return textDocument("api-ingest", sha256(rawText.getBytes(StandardCharsets.UTF_8)), rawText, "API_TEXT");
    }

    private ExtractedDocument textDocument(String filename, String sha256, String text, String method) {
        if (text.isBlank()) {
            throw new DocumentRejectedException(filename + " contains no readable text.");
        }
        boolean truncated = text.length() > maxInputChars;
        String sent = truncated ? text.substring(0, maxInputChars) : text;
        return new ExtractedDocument(filename, sha256, sent, List.of(), method, truncated);
    }

    private ExtractedDocument extractPdf(String filename, String sha256, byte[] bytes) throws IOException {
        try (PDDocument pdf = Loader.loadPDF(bytes)) {
            String text = new PDFTextStripper().getText(pdf);
            if (text.strip().length() >= MIN_PDF_TEXT_CHARS) {
                return textDocument(filename, sha256, text, "PDF_TEXT");
            }

            // Scanned PDF: no text layer, so render pages for the vision model
            PDFRenderer renderer = new PDFRenderer(pdf);
            int pageCount = pdf.getNumberOfPages();
            int pagesSent = Math.min(pageCount, maxImages);
            List<DocumentImage> images = new ArrayList<>();
            for (int page = 0; page < pagesSent; page++) {
                BufferedImage image = renderer.renderImageWithDPI(page, RENDER_DPI, ImageType.RGB);
                images.add(new DocumentImage("image/jpeg", Base64.getEncoder().encodeToString(toJpeg(image))));
            }
            String placeholder = "[Scanned PDF " + filename + ": " + pageCount + " page(s), first "
                    + pagesSent + " sent to the vision model as images]";
            return new ExtractedDocument(filename, sha256, placeholder, images, "PDF_SCANNED", pageCount > pagesSent);
        } catch (InvalidPasswordException e) {
            throw new DocumentRejectedException(filename + " is password-protected. Upload an unlocked copy.", e);
        }
    }

    private ExtractedDocument imageDocument(String filename, String sha256, byte[] bytes, String mediaType) {
        if (bytes.length > MAX_IMAGE_BYTES) {
            throw new DocumentRejectedException(filename + " is larger than 4 MB. Upload a smaller image or a PDF.");
        }
        DocumentImage image = new DocumentImage(mediaType, Base64.getEncoder().encodeToString(bytes));
        return new ExtractedDocument(filename, sha256, "[Image " + filename + " sent to the vision model]",
                List.of(image), "IMAGE", false);
    }

    private String extractSpreadsheetText(byte[] bytes) throws IOException {
        StringBuilder sb = new StringBuilder();
        // DataFormatter shows numbers as they appear in Excel (1,000,000 rather than 1.0E6)
        DataFormatter formatter = new DataFormatter(Locale.US);
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            for (Sheet sheet : workbook) {
                sb.append("--- Sheet: ").append(sheet.getSheetName()).append(" ---\n");
                for (Row row : sheet) {
                    for (Cell cell : row) {
                        sb.append(formatCell(formatter, evaluator, cell)).append("\t");
                    }
                    sb.append("\n");
                }
            }
        }
        return sb.toString();
    }

    private static String formatCell(DataFormatter formatter, FormulaEvaluator evaluator, Cell cell) {
        try {
            return formatter.formatCellValue(cell, evaluator);
        } catch (RuntimeException e) {
            // Formula POI can't evaluate: fall back to the cached value
            return formatter.formatCellValue(cell);
        }
    }

    private static byte[] toJpeg(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available on the JVM", e);
        }
    }
}
