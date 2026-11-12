package com.vault.audit_engine.config;

import com.vault.audit_engine.model.User;
import com.vault.audit_engine.service.AccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final AccountService accounts;

    public CustomAuthenticationSuccessHandler(AccountService accounts) {
        this.accounts = accounts;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        VaultUserDetails user = (VaultUserDetails) authentication.getPrincipal();

        // Display-only copies for the UI. Authorization always uses the principal, not these.
        HttpSession session = request.getSession();
        session.setAttribute("CURRENT_USER_TENANT", user.getTenantId());
        session.setAttribute("USER_FULL_NAME", user.getFullName());

        accounts.recordLogin(user.getUsername());

        // The operator manages companies; everyone else lands on the audit dashboard
        boolean operator = user.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(User.ROLE_PLATFORM_ADMIN));
        response.sendRedirect(operator ? "/admin.html" : "/code.html");
    }
}
