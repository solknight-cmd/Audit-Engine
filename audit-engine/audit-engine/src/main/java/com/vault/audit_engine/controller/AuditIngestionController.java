package com.vault.audit_engine.controller;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import com.vault.audit_engine.service.DocumentExtractionService;
import org.springframework.web.multipart.MultipartFile;
import com.vault.audit_engine.service.VaultProcessingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/audits")
@CrossOrigin(origins = "*")
public class AuditIngestionController {

    private final VaultProcessingService processingService;
    private final DocumentExtractionService extractionService;

    // Make sure your constructor accepts both fields now:
    public AuditIngestionController(VaultProcessingService processingService,
                                    DocumentExtractionService extractionService) {
        this.processingService = processingService;
        this.extractionService = extractionService;
    }

    // Ingestion Request Contract (Payload DTO)
    public record IngestionRequest(
            @NotBlank(message = "companyId is required") String companyId,
            @NotBlank(message = "documentType is required") String documentType,
            @NotBlank(message = "rawText is required") String rawText
    ) {}

    @PostMapping("/ingest")
    @ResponseStatus(HttpStatus.ACCEPTED) // Explicitly returns 202 Accepted status
    public String ingestStatement(@Valid @RequestBody IngestionRequest request) {

        // Push processing off to a background thread instantly
        processingService.processAndVault(
                request.companyId(),
                request.documentType(),
                request.rawText()
        );

        // Immediate acknowledgment back to the enterprise client application
        return "Financial statement received successfully. Audit processing has been queued in the background.";
    }

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public String uploadFinancialStatement(
            @RequestParam("companyId") String companyId,
            @RequestParam("documentType") String documentType,
            @RequestParam("file") MultipartFile file) {

        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file cannot be empty.");
        }

        // 1. Extract structural text out of supported files
        String rawText = extractionService.extractText(file);

        // 2. Offload straight to our background processing service thread loop
        processingService.processAndVaultFile(companyId, documentType, file, rawText);

        return "File uploaded successfully. Financial audit processing pipeline has been queued.";
    }

    @GetMapping("/vault")
    public List<com.vault.audit_engine.model.AuditRecord> getAllVaultRecords() {
        return processingService.getCompleteVaultLedger();
    }
}
