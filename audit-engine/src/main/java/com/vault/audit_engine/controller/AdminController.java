package com.vault.audit_engine.controller;

import com.vault.audit_engine.config.VaultUserDetails;
import com.vault.audit_engine.service.AccountService;
import com.vault.audit_engine.service.AccountService.CompanyCreated;
import com.vault.audit_engine.service.AccountService.CompanySummary;
import com.vault.audit_engine.service.AccountService.LinkResult;
import com.vault.audit_engine.service.AccountService.UserSummary;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

/** The operator's tools: client companies and any company's users. Restricted to ROLE_PLATFORM_ADMIN in SecurityConfig. */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final AccountService accounts;

    public AdminController(AccountService accounts) {
        this.accounts = accounts;
    }

    public record NewCompany(String companyName, String allowedDomain, String adminEmail, String adminName) {}

    public record NewUser(String email, String fullName, String role) {}

    @GetMapping("/companies")
    public List<CompanySummary> companies() {
        return accounts.listCompanies();
    }

    @PostMapping("/companies")
    @ResponseStatus(HttpStatus.CREATED)
    public CompanyCreated createCompany(@AuthenticationPrincipal VaultUserDetails operator, @RequestBody NewCompany request) {
        return accounts.createCompany(request.companyName(), request.allowedDomain(), request.adminEmail(),
                request.adminName(), operator.getUsername(), baseUrl());
    }

    @GetMapping("/companies/{companyId}/users")
    public List<UserSummary> users(@PathVariable String companyId) {
        accounts.requireCompany(companyId);
        return accounts.listUsers(companyId);
    }

    @PostMapping("/companies/{companyId}/users")
    @ResponseStatus(HttpStatus.CREATED)
    public LinkResult invite(@AuthenticationPrincipal VaultUserDetails operator, @PathVariable String companyId,
                             @RequestBody NewUser request) {
        return accounts.invite(companyId, request.email(), request.fullName(), request.role(), operator.getUsername(), baseUrl());
    }

    @PostMapping("/companies/{companyId}/users/{userId}/reset-link")
    public LinkResult resetLink(@AuthenticationPrincipal VaultUserDetails operator, @PathVariable String companyId,
                                @PathVariable Long userId) {
        return accounts.createResetLink(companyId, userId, operator.getUsername(), baseUrl());
    }

    @PostMapping("/companies/{companyId}/users/{userId}/disable")
    public UserSummary disable(@AuthenticationPrincipal VaultUserDetails operator, @PathVariable String companyId,
                               @PathVariable Long userId) {
        return accounts.setEnabled(companyId, userId, false, operator.getUsername());
    }

    @PostMapping("/companies/{companyId}/users/{userId}/enable")
    public UserSummary enable(@AuthenticationPrincipal VaultUserDetails operator, @PathVariable String companyId,
                              @PathVariable Long userId) {
        return accounts.setEnabled(companyId, userId, true, operator.getUsername());
    }

    /** Links are built from the address the admin is using, e.g. http://localhost:8080 or the hosted domain. */
    static String baseUrl() {
        return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
    }
}
