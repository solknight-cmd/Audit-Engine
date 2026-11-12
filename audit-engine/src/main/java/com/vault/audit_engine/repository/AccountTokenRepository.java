package com.vault.audit_engine.repository;

import com.vault.audit_engine.model.AccountToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccountTokenRepository extends JpaRepository<AccountToken, Long> {
    Optional<AccountToken> findByTokenHash(String tokenHash);

    List<AccountToken> findByUserIdAndUsedAtIsNull(Long userId);
}
