package com.vault.audit_engine.config;

import com.vault.audit_engine.model.User;
import com.vault.audit_engine.repository.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;

    public CustomAuthenticationSuccessHandler(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {

        String email = authentication.getName();

        // Pull the real profile from the database to look up company affinity
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ServletException("Corporate security profile lost on context swap"));

        // Store their specific company tenant string securely in the server session context!
        HttpSession session = request.getSession();
        session.setAttribute("CURRENT_USER_TENANT", user.getTenant().getTenantId());
        session.setAttribute("USER_FULL_NAME", user.getFullName());

        // Securely redirect to your primary UI control center dashboard script
        response.sendRedirect("/code.html");
    }
}
