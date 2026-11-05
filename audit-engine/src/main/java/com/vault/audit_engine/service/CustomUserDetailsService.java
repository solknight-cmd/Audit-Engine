package com.vault.audit_engine.service;

import com.vault.audit_engine.config.VaultUserDetails;
import com.vault.audit_engine.model.User;
import com.vault.audit_engine.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

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

        // Role is stored with its ROLE_ prefix, e.g. ROLE_AUDITOR
        return new VaultUserDetails(
                user.getEmail(),
                user.getPassword(), // Hashed BCrypt text token from DB
                List.of(new SimpleGrantedAuthority(user.getRole())),
                user.getTenant().getTenantId(),
                user.getTenant().getCompanyName(),
                user.getFullName()
        );
    }
}
