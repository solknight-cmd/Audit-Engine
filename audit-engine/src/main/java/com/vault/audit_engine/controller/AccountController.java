package com.vault.audit_engine.controller;

import com.vault.audit_engine.config.VaultUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AccountController {

    public record CurrentUser(String email, String fullName, String companyId, String companyName) {}

    // Who is logged in, for the dashboard header
    @GetMapping("/me")
    public CurrentUser me(@AuthenticationPrincipal VaultUserDetails user) {
        return new CurrentUser(user.getUsername(), user.getFullName(), user.getTenantId(), user.getCompanyName());
    }
}
