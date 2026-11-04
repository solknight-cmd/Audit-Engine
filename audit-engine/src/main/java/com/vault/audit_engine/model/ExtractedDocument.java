package com.vault.audit_engine.model;

import java.util.List;

/**
 * What we pulled out of an uploaded document, ready to send to the AI model.
 *
 * @param text      extracted text, or a short placeholder when the content is only in {@code images}
 * @param images    page images for the vision model (scanned PDFs and photos); empty for text documents
 * @param method    how the content was obtained, e.g. PDF_TEXT, PDF_SCANNED, SPREADSHEET, IMAGE
 * @param truncated true when text or pages were cut to fit the model's limits
 */
public record ExtractedDocument(
        String sourceName,
        String sha256,
        String text,
        List<DocumentImage> images,
        String method,
        boolean truncated
) {
    public record DocumentImage(String mediaType, String base64Data) {}

    public boolean hasImages() {
        return !images.isEmpty();
    }
}
