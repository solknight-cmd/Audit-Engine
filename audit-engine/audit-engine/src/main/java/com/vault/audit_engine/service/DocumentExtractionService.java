package com.vault.audit_engine.service;

import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

@Service
public class DocumentExtractionService {

    public String extractText(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null) {
            throw new IllegalArgumentException("Cannot process a file without a valid name.");
        }

        try {
            if (filename.endsWith(".xlsx") || filename.endsWith(".xls")) {
                return extractExcelText(file);
            } else if (filename.endsWith(".txt") || filename.endsWith(".csv")) {
                return extractPlainText(file);
            } else if (filename.endsWith(".pdf") || filename.endsWith(".png") || filename.endsWith(".jpg")) {
                // For enterprise-grade scaling, PDFs and images go straight to the AI text extractor
                return "[MULTIPART BINARY DOCUMENT: " + filename + "]";
            } else {
                throw new UnsupportedOperationException("Unsupported file format: " + filename);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to extract content from file: " + filename, e);
        }
    }

    private String extractPlainText(MultipartFile file) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }

    private String extractExcelText(MultipartFile file) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            for (Sheet sheet : workbook) {
                sb.append("--- Sheet: ").append(sheet.getSheetName()).append(" ---\n");
                for (Row row : sheet) {
                    for (Cell cell : row) {
                        sb.append(cell.toString()).append("\t");
                    }
                    sb.append("\n");
                }
            }
        }
        return sb.toString();
    }
}
