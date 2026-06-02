package com.vault.audit_engine.controller;

import com.vault.audit_engine.service.SseLogService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*") // Allows clean streaming handshakes without CORS issues
public class SseController {

    private final SseLogService sseLogService;

    public SseController(SseLogService sseLogService) {
        this.sseLogService = sseLogService;
    }

    @GetMapping(value = "/forensic-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamForensicLogs() {
        return sseLogService.registerClient();
    }
}
