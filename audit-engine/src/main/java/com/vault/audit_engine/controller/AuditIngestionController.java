package com.vault.audit_engine.controller;

import com.vault.audit_engine.config.VaultUserDetails;
import com.vault.audit_engine.model.AuditRecord;
import com.vault.audit_engine.service.DocumentExtractionService;
import com.vault.audit_engine.service.VaultProcessingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

// Every endpoint works on the caller's own tenant, taken from their login, never from the request.
@RestController
@RequestMapping("/api/v1/audits")
public class AuditIngestionController {

    private final VaultProcessingService processingService;
    private final DocumentExtractionService extractionService;

    public AuditIngestionController(VaultProcessingService processingService,
                                    DocumentExtractionService extractionService) {
        this.processingService = processingService;
        this.extractionService = extractionService;
    }

    // Ingestion Request Contract (Payload DTO)
    public record IngestionRequest(
            @NotBlank(message = "documentType is required") String documentType,
            @NotBlank(message = "rawText is required") String rawText
    ) {}

    @PostMapping("/ingest")
    @ResponseStatus(HttpStatus.ACCEPTED) // Explicitly returns 202 Accepted status
    public String ingestStatement(@AuthenticationPrincipal VaultUserDetails user,
                                  @Valid @RequestBody IngestionRequest request) {

        // Push processing off to a background thread instantly
        processingService.processAndVault(user.getTenantId(), request.documentType(), request.rawText());

        // Immediate acknowledgment back to the enterprise client application
        return "Financial statement received successfully. Audit processing has been queued in the background.";
    }

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public String uploadFinancialStatement(
            @AuthenticationPrincipal VaultUserDetails user,
            @RequestParam("documentType") String documentType,
            @RequestParam("file") MultipartFile file) {

        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file cannot be empty.");
        }

        // 1. Extract structural text out of supported files
        String rawText = extractionService.extractText(file);

        // 2. Hand off only the filename and text: the uploaded file is deleted once this request ends
        processingService.processAndVaultFile(user.getTenantId(), documentType, file.getOriginalFilename(), rawText);

        return "File uploaded successfully. Financial audit processing pipeline has been queued.";
    }

    @GetMapping("/vault")
    public List<AuditRecord> getVaultRecords(@AuthenticationPrincipal VaultUserDetails user) {
        return processingService.getVaultLedger(user.getTenantId());
    }
}
