package com.vault.audit_engine.repository;

import com.vault.audit_engine.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    // Crucial for Spring Security mapping lookups; emails are stored in lower case
    Optional<User> findByEmailIgnoreCase(String email);

    List<User> findByTenantTenantIdOrderByCreatedAtAsc(String tenantId);

    long countByTenantTenantId(String tenantId);

    List<User> findByRole(String role);
}
