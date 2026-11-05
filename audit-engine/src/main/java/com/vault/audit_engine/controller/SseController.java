package com.vault.audit_engine.controller;

import com.vault.audit_engine.config.VaultUserDetails;
import com.vault.audit_engine.service.SseLogService;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1")
public class SseController {

    private final SseLogService sseLogService;

    public SseController(SseLogService sseLogService) {
        this.sseLogService = sseLogService;
    }

    // Each user only receives pipeline logs for their own company
    @GetMapping(value = "/forensic-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamForensicLogs(@AuthenticationPrincipal VaultUserDetails user) {
        return sseLogService.registerClient(user.getTenantId());
    }
}
