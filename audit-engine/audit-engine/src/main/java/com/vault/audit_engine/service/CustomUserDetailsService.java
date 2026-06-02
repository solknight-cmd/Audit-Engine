package com.vault.audit_engine.service;

import com.vault.audit_engine.model.User;
import com.vault.audit_engine.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        // Query user table record securely by email identity
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("No corporate profile mapped to email: " + email));

        // Return a spring security-compliant user identity context block
        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPassword()) // Hashed BCrypt text token from DB
                .roles(user.getRole().replace("ROLE_", ""))
                .build();
    }
}
