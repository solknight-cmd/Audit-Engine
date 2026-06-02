package com.vault.audit_engine.repository;

import com.vault.audit_engine.model.AuditRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditRecordRepository extends JpaRepository<AuditRecord, Long> {

    @Override
    default void delete(AuditRecord entity) {
        throw new UnsupportedOperationException("VAULT COMPLIANCE ERROR: Records cannot be deleted.");
    }

    @Override
    default void deleteById(Long id) {
        throw new UnsupportedOperationException("VAULT COMPLIANCE ERROR: Records cannot be deleted.");
    }

    @Override
    default void deleteAll() {
        throw new UnsupportedOperationException("VAULT COMPLIANCE ERROR: Records cannot be wiped.");
    }
}
