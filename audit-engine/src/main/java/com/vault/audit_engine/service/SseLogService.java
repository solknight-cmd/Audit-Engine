package com.vault.audit_engine.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class SseLogService {

    // Active browser connections, grouped by tenant so logs never cross companies
    private final Map<String, List<SseEmitter>> emittersByTenant = new ConcurrentHashMap<>();
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private final ObjectMapper objectMapper;

    public SseLogService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Registers a new browser client session tab for real-time streaming updates
     */
    public SseEmitter registerClient(String tenantId) {
        SseEmitter emitter = new SseEmitter(24 * 60 * 60 * 1000L); // 24-hour timeout
        List<SseEmitter> emitters = emittersByTenant.computeIfAbsent(tenantId, id -> new CopyOnWriteArrayList<>());
        emitters.add(emitter);

        // Remove emitter when it completes or times out
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError((e) -> emitters.remove(emitter));

        // Send initialization packet to confirm socket connection active
        try {
            emitter.send(event("CONNECT: SECURE_SSE_LOG_STREAM_INITIALIZED"));
        } catch (IOException e) {
            emitters.remove(emitter);
        }

        return emitter;
    }

    /**
     * Broadcasts an audit tracking notification message packet to the tenant's open browser views
     */
    public void log(String tenantId, String message) {
        List<SseEmitter> emitters = emittersByTenant.get(tenantId);
        if (emitters == null) {
            return;
        }

        SseEmitter.SseEventBuilder event = event(message);
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(event);
            } catch (IOException e) {
                emitters.remove(emitter);
            }
        }
    }

    private SseEmitter.SseEventBuilder event(String message) {
        String timestamp = LocalTime.now().format(timeFormatter);
        // Serialized with Jackson so quotes or newlines in messages can't break the JSON
        String jsonPayload;
        try {
            jsonPayload = objectMapper.writeValueAsString(Map.of("time", "[" + timestamp + "]", "message", message));
        } catch (IOException e) {
            throw new IllegalStateException("Could not serialize log message", e);
        }
        return SseEmitter.event().name("forensic-log").data(jsonPayload);
    }
}
