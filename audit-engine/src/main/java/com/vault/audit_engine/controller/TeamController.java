package com.vault.audit_engine.controller;

import com.vault.audit_engine.config.VaultUserDetails;
import com.vault.audit_engine.controller.AdminController.NewUser;
import com.vault.audit_engine.service.AccountService;
import com.vault.audit_engine.service.AccountService.LinkResult;
import com.vault.audit_engine.service.AccountService.UserSummary;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * A company admin's view of their own company's users. Restricted to ROLE_COMPANY_ADMIN in SecurityConfig;
 * the company always comes from the admin's login, so they can't reach another company's users.
 */
@RestController
@RequestMapping("/api/v1/team")
public class TeamController {

    private final AccountService accounts;

    public TeamController(AccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping("/users")
    public List<UserSummary> users(@AuthenticationPrincipal VaultUserDetails admin) {
        return accounts.listUsers(admin.getTenantId());
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public LinkResult invite(@AuthenticationPrincipal VaultUserDetails admin, @RequestBody NewUser request) {
        return accounts.invite(admin.getTenantId(), request.email(), request.fullName(), request.role(),
                admin.getUsername(), AdminController.baseUrl());
    }

    @PostMapping("/users/{userId}/reset-link")
    public LinkResult resetLink(@AuthenticationPrincipal VaultUserDetails admin, @PathVariable Long userId) {
        return accounts.createResetLink(admin.getTenantId(), userId, admin.getUsername(), AdminController.baseUrl());
    }

    @PostMapping("/users/{userId}/disable")
    public UserSummary disable(@AuthenticationPrincipal VaultUserDetails admin, @PathVariable Long userId) {
        return accounts.setEnabled(admin.getTenantId(), userId, false, admin.getUsername());
    }

    @PostMapping("/users/{userId}/enable")
    public UserSummary enable(@AuthenticationPrincipal VaultUserDetails admin, @PathVariable Long userId) {
        return accounts.setEnabled(admin.getTenantId(), userId, true, admin.getUsername());
    }
}
