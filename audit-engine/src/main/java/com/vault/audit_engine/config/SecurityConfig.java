package com.vault.audit_engine.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.ExceptionMappingAuthenticationFailureHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.session.SessionInformationExpiredStrategy;

import java.util.Map;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final CustomAuthenticationSuccessHandler successHandler;

    // Inject our multi-tenant session handler components
    public SecurityConfig(CustomAuthenticationSuccessHandler successHandler) {
        this.successHandler = successHandler;
    }

    /**
     * Locked accounts (see LoginAttemptService) and disabled accounts get their own message;
     * every other failure stays generic so it doesn't reveal which emails have accounts.
     */
    private static AuthenticationFailureHandler loginFailureHandler() {
        ExceptionMappingAuthenticationFailureHandler handler = new ExceptionMappingAuthenticationFailureHandler();
        handler.setExceptionMappings(Map.of(
                LockedException.class.getName(), "/Login.html?locked",
                DisabledException.class.getName(), "/Login.html?disabled"));
        handler.setDefaultFailureUrl("/Login.html?error");
        return handler;
    }

    /** A session ended by an admin (disable or password reset): 401 for API calls, the login page otherwise. */
    private static SessionInformationExpiredStrategy expiredSessionStrategy() {
        return event -> {
            if (event.getRequest().getRequestURI().startsWith("/api/")) {
                event.getResponse().sendError(HttpStatus.UNAUTHORIZED.value());
            } else {
                event.getResponse().sendRedirect("/Login.html?expired");
            }
        };
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, SessionRegistry sessionRegistry) throws Exception {
        http
                // CSRF token in a readable XSRF-TOKEN cookie. Pages send it back as the X-XSRF-TOKEN
                // header (fetch) or the _csrf field (login form); another site can't read the cookie.
                // The plain handler accepts the cookie value as-is, which static pages need.
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                )
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        // Public: marketing site, login page and static assets. Dashboards and the API need a login.
                        .requestMatchers(
                                "/",
                                "/Login.html",
                                "/set-password.html",
                                "/api/v1/auth/token",
                                "/api/v1/auth/set-password",
                                "/landing/**",
                                "/error",
                                "/css/**",
                                "/js/**"
                        ).permitAll()
                        // The operator's pages and API
                        .requestMatchers("/admin.html", "/api/v1/admin/**").hasRole("PLATFORM_ADMIN")
                        // A company admin's team management
                        .requestMatchers("/team.html", "/api/v1/team/**").hasRole("COMPANY_ADMIN")
                        // Running and reading audits: auditors and company admins, not the operator
                        .requestMatchers("/api/v1/audits/**", "/api/v1/metrics/**", "/api/v1/forensic-stream")
                                .hasAnyRole("AUDITOR", "COMPANY_ADMIN")
                        .anyRequest().authenticated()
                )
                // Lets AccountService sign people out of every session when they're disabled or reset
                .sessionManagement(session -> session
                        .maximumSessions(-1)
                        .sessionRegistry(sessionRegistry)
                        .expiredSessionStrategy(expiredSessionStrategy())
                )
                // API calls without a session get 401 instead of a redirect to the login page
                .exceptionHandling(ex -> ex.defaultAuthenticationEntryPointFor(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                        PathPatternRequestMatcher.withDefaults().matcher("/api/**")
                ))
                .formLogin(form -> form
                        .loginPage("/Login.html") // Must match the file's case: jar resources are case-sensitive
                        .loginProcessingUrl("/api/v1/auth/login")
                        // Register our custom enterprise tenant session routing rule here!
                        .successHandler(successHandler)
                        .failureHandler(loginFailureHandler())
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/api/v1/auth/logout")
                        .logoutSuccessUrl("/Login.html?logout=true")
                        .permitAll()
                );

        return http.build();
    }
}
