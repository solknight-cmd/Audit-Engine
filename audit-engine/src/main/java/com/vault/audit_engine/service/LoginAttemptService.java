package com.vault.audit_engine.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Locks an account after repeated wrong passwords, to stop password guessing. Counts are kept in
 * memory, so they reset when the app restarts and aren't shared between servers; fine for one instance.
 */
@Service
public class LoginAttemptService {

    private static final Logger logger = LoggerFactory.getLogger(LoginAttemptService.class);

    private record Failures(int count, Instant firstFailure, Instant lockedUntil) {}

    private final Map<String, Failures> failuresByEmail = new ConcurrentHashMap<>();
    private final int maxFailures;
    private final Duration window;
    private final Clock clock;

    @Autowired
    public LoginAttemptService(@Value("${vault.login.max-failures:5}") int maxFailures,
                               @Value("${vault.login.lockout:15m}") Duration window) {
        this(maxFailures, window, Clock.systemUTC());
    }

    LoginAttemptService(int maxFailures, Duration window, Clock clock) {
        this.maxFailures = maxFailures;
        this.window = window;
        this.clock = clock;
    }

    public boolean isLocked(String email) {
        Failures failures = failuresByEmail.get(key(email));
        return failures != null && failures.lockedUntil() != null && clock.instant().isBefore(failures.lockedUntil());
    }

    @EventListener
    public void onFailure(AuthenticationFailureBadCredentialsEvent event) {
        String email = key(String.valueOf(event.getAuthentication().getPrincipal()));
        Instant now = clock.instant();
        Failures updated = failuresByEmail.compute(email, (k, previous) -> {
            // Failures older than the window, or from before an expired lock, start a fresh count
            boolean stale = previous == null || previous.firstFailure().plus(window).isBefore(now)
                    || (previous.lockedUntil() != null && !now.isBefore(previous.lockedUntil()));
            int count = stale ? 1 : previous.count() + 1;
            Instant first = stale ? now : previous.firstFailure();
            return new Failures(count, first, count >= maxFailures ? now.plus(window) : null);
        });
        if (updated.lockedUntil() != null && updated.count() == maxFailures) {
            logger.warn("Login locked for {} after {} failed attempts", email, maxFailures);
        }
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        failuresByEmail.remove(key(event.getAuthentication().getName()));
    }

    private static String key(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
