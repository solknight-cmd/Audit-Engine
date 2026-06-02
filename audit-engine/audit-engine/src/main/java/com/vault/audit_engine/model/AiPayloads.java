package com.vault.audit_engine.model;

import java.util.List;

public class AiPayloads {

    public record ChatRequest(String model, List<Message> messages, double temperature) {}

    public record Message(String role, String content) {}

    // A content block can represent text or a multi-format document/image payload
    public record Content(String type, String text, Document input_document) {
        public static Content text(String text) {
            return new Content("text", text, null);
        }
        public static Content document(String base64Data, String mediaType) {
            return new Content("document", null, new Document("base64", new Source(mediaType, base64Data)));
        }
    }

    public record Document(String type, Source source) {}
    public record Source(String media_type, String data) {}

    public record ChatResponse(List<Choice> choices) {}
    public record Choice(MessageResponse message) {}
    public record MessageResponse(String role, String content) {}
}
