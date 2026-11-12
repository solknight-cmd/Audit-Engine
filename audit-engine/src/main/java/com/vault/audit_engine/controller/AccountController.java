package com.vault.audit_engine.controller;

import com.vault.audit_engine.config.VaultUserDetails;
import com.vault.audit_engine.service.AccountException;
import com.vault.audit_engine.service.AccountService;
import com.vault.audit_engine.service.AccountService.TokenInfo;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class AccountController {

    private final AccountService accounts;

    public AccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    public record CurrentUser(String email, String fullName, String companyId, String companyName, String role) {}

    public record PasswordChange(String currentPassword, String newPassword) {}

    public record SetPassword(String token, String fullName, String password) {}

    // Who is logged in, for the dashboard header and to show admin-only links
    @GetMapping("/me")
    public CurrentUser me(@AuthenticationPrincipal VaultUserDetails user) {
        String role = user.getAuthorities().stream().map(GrantedAuthority::getAuthority).findFirst().orElse("");
        return new CurrentUser(user.getUsername(), user.getFullName(), user.getTenantId(), user.getCompanyName(), role);
    }

    @PostMapping("/me/password")
    public Map<String, String> changePassword(@AuthenticationPrincipal VaultUserDetails user, @RequestBody PasswordChange request) {
        accounts.changeOwnPassword(user.getUsername(), request.currentPassword(), request.newPassword());
        return Map.of("message", "Password changed.");
    }

    // Public: used by set-password.html, opened from an invitation or reset link

    @GetMapping("/auth/token")
    public TokenInfo describeLink(@RequestParam("token") String token) {
        return accounts.describeToken(token);
    }

    @PostMapping("/auth/set-password")
    public Map<String, String> setPassword(@RequestBody SetPassword request) {
        accounts.setPasswordWithToken(request.token(), request.fullName(), request.password());
        return Map.of("message", "Password set. You can sign in now.");
    }

    /** Account errors carry a message written for the user; return it as {"error": ...}. */
    @RestControllerAdvice(assignableTypes = {AccountController.class, AdminController.class, TeamController.class})
    static class AccountErrors {
        @ExceptionHandler(AccountException.class)
        ResponseEntity<Map<String, String>> handle(AccountException e) {
            return ResponseEntity.status(e.getStatus()).body(Map.of("error", e.getMessage()));
        }
    }
}
