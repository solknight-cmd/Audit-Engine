package com.vault.audit_engine.repository;

import com.vault.audit_engine.model.AuditRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

// Append-only: the database also rejects UPDATE/DELETE/TRUNCATE on this table (see V1 migration).
// These overrides just fail fast in Java before a query is sent.
@Repository
public interface AuditRecordRepository extends JpaRepository<AuditRecord, Long> {

    List<AuditRecord> findByCompanyIdOrderByVaultedAtDesc(String companyId);

    long countByCompanyId(String companyId);

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

    @Override
    default void deleteAll(Iterable<? extends AuditRecord> entities) {
        throw new UnsupportedOperationException("VAULT COMPLIANCE ERROR: Records cannot be wiped.");
    }

    @Override
    default void deleteAllById(Iterable<? extends Long> ids) {
        throw new UnsupportedOperationException("VAULT COMPLIANCE ERROR: Records cannot be wiped.");
    }

    @Override
    default void deleteAllInBatch() {
        throw new UnsupportedOperationException("VAULT COMPLIANCE ERROR: Records cannot be wiped.");
    }

    @Override
    default void deleteAllInBatch(Iterable<AuditRecord> entities) {
        throw new UnsupportedOperationException("VAULT COMPLIANCE ERROR: Records cannot be wiped.");
    }

    @Override
    default void deleteAllByIdInBatch(Iterable<Long> ids) {
        throw new UnsupportedOperationException("VAULT COMPLIANCE ERROR: Records cannot be wiped.");
    }
}
