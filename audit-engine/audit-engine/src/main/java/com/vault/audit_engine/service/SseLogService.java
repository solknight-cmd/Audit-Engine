package com.vault.audit_engine.service;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class SseLogService {

    // Thread-safe list of active browser connections
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    /**
     * Registers a new browser client session tab for real-time streaming updates
     */
    public SseEmitter registerClient() {
        SseEmitter emitter = new SseEmitter(24 * 60 * 60 * 1000L); // 24-hour timeout
        this.emitters.add(emitter);

        // Remove emitter when it completes or times out
        emitter.onCompletion(() -> this.emitters.remove(emitter));
        emitter.onTimeout(() -> this.emitters.remove(emitter));
        emitter.onError((e) -> this.emitters.remove(emitter));

        // Send initialization packet to confirm socket connection active
        try {
            String timestamp = LocalTime.now().format(timeFormatter);
            emitter.send(SseEmitter.event()
                    .name("forensic-log")
                    .data(String.format("{\"time\":\"%s\", \"message\":\"CONNECT: SECURE_SSE_LOG_STREAM_INITIALIZED\"}", timestamp)));
        } catch (IOException e) {
            this.emitters.remove(emitter);
        }

        return emitter;
    }

    /**
     * Broadcasts an audit tracking notification message packet to all active browser views
     */
    public void log(String message) {
        String timestamp = LocalTime.now().format(timeFormatter);
        // JSON structured layout payload packet
        String jsonPayload = String.format("{\"time\":\"[%s]\", \"message\":\"%s\"}", timestamp, message);

        List<SseEmitter> deadEmitters = new ArrayList<>();

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("forensic-log")
                        .data(jsonPayload));
            } catch (IOException e) {
                deadEmitters.add(emitter);
            }
        }

        this.emitters.removeAll(deadEmitters);
    }
}
