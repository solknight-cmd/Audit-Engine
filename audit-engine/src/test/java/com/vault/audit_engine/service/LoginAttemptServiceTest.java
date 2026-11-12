package com.vault.audit_engine.service;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class LoginAttemptServiceTest {

    /** A clock the test can move forward. */
    private static class TestClock extends Clock {
        Instant now = Instant.parse("2026-11-12T10:00:00Z");
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private final TestClock clock = new TestClock();
    private final LoginAttemptService service = new LoginAttemptService(5, Duration.ofMinutes(15), clock);

    private void fail(String email) {
        service.onFailure(new AuthenticationFailureBadCredentialsEvent(
                new UsernamePasswordAuthenticationToken(email, "wrong"), new BadCredentialsException("bad")));
    }

    @Test
    void locksAfterFiveFailuresAndUnlocksAfterFifteenMinutes() {
        for (int i = 0; i < 4; i++) fail("a@x.com");
        assertFalse(service.isLocked("a@x.com"));

        fail("A@X.com"); // email case doesn't matter
        assertTrue(service.isLocked("a@x.com"));

        clock.now = clock.now.plus(Duration.ofMinutes(16));
        assertFalse(service.isLocked("a@x.com"));
    }

    @Test
    void failuresSpreadOverMoreThanFifteenMinutesDontLock() {
        for (int i = 0; i < 4; i++) fail("b@x.com");
        clock.now = clock.now.plus(Duration.ofMinutes(20));
        fail("b@x.com");
        assertFalse(service.isLocked("b@x.com"));
    }

    @Test
    void successfulLoginResetsTheCount() {
        for (int i = 0; i < 4; i++) fail("c@x.com");
        service.onSuccess(new AuthenticationSuccessEvent(new UsernamePasswordAuthenticationToken("c@x.com", null, java.util.List.of())));
        fail("c@x.com");
        assertFalse(service.isLocked("c@x.com"));
    }

    @Test
    void oneAccountsFailuresDontLockAnother() {
        for (int i = 0; i < 5; i++) fail("d@x.com");
        assertFalse(service.isLocked("e@x.com"));
    }
}
