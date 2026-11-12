package com.vault.audit_engine.service;

import org.springframework.http.HttpStatus;

/** A request about accounts that can't be carried out; the message is safe to show to the user. */
public class AccountException extends RuntimeException {

    private final HttpStatus status;

    public AccountException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    static AccountException badRequest(String message) {
        return new AccountException(HttpStatus.BAD_REQUEST, message);
    }

    static AccountException notFound(String message) {
        return new AccountException(HttpStatus.NOT_FOUND, message);
    }

    static AccountException conflict(String message) {
        return new AccountException(HttpStatus.CONFLICT, message);
    }
}
