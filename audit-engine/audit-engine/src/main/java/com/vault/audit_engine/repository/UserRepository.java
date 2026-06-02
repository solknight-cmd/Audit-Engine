package com.vault.audit_engine.repository;

import com.vault.audit_engine.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    // Crucial for Spring Security mapping lookups
    Optional<User> findByEmail(String email);
}
