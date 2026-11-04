package com.vault.audit_engine.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final CustomAuthenticationSuccessHandler successHandler;

    // Inject our multi-tenant session handler components
    public SecurityConfig(CustomAuthenticationSuccessHandler successHandler) {
        this.successHandler = successHandler;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // TODO: re-enable CSRF once the dashboard calls the API (it needs to send the token).
                // Until then, the SameSite=Lax session cookie (application.properties) blocks
                // cross-site form posts.
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        // Public: marketing site, login page and static assets. Dashboards and the API need a login.
                        .requestMatchers(
                                "/",
                                "/Login.html",
                                "/landing/**",
                                "/error",
                                "/css/**",
                                "/js/**"
                        ).permitAll()
                        .anyRequest().authenticated()
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
