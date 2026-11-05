package com.vault.audit_engine.controller;

import com.vault.audit_engine.config.VaultUserDetails;
import com.vault.audit_engine.model.AuditRecord;
import com.vault.audit_engine.model.ExtractedDocument;
import com.vault.audit_engine.service.DocumentExtractionService;
import com.vault.audit_engine.service.DocumentRejectedException;
import com.vault.audit_engine.service.VaultProcessingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

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
        processingService.process(user.getTenantId(), request.documentType(),
                extractionService.fromText(request.rawText()));

        // Immediate acknowledgment back to the enterprise client application
        return "Financial statement received successfully. Audit processing has been queued in the background.";
    }

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public String uploadFinancialStatement(
            @AuthenticationPrincipal VaultUserDetails user,
            @RequestParam("documentType") String documentType,
            @RequestParam("file") MultipartFile file) {

        // Extract here, while the upload still exists: it is deleted once this request ends
        ExtractedDocument document = extractionService.extract(file);
        processingService.process(user.getTenantId(), documentType, document);

        return "File uploaded successfully. Financial audit processing pipeline has been queued.";
    }

    @GetMapping("/vault")
    public List<AuditRecord> getVaultRecords(@AuthenticationPrincipal VaultUserDetails user) {
        return processingService.getVaultLedger(user.getTenantId());
    }

    @ExceptionHandler(DocumentRejectedException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleRejectedDocument(DocumentRejectedException e) {
        return Map.of("error", e.getMessage());
    }
}
