package com.vault.audit_engine.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        VaultUserDetails user = (VaultUserDetails) authentication.getPrincipal();

        // Display-only copies for the UI. Authorization always uses the principal, not these.
        HttpSession session = request.getSession();
        session.setAttribute("CURRENT_USER_TENANT", user.getTenantId());
        session.setAttribute("USER_FULL_NAME", user.getFullName());

        // Securely redirect to your primary UI control center dashboard script
        response.sendRedirect("/code.html");
    }
}
