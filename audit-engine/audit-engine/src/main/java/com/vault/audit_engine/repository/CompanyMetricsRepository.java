package com.vault.audit_engine.repository;

import com.vault.audit_engine.model.CompanyMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface CompanyMetricsRepository extends JpaRepository<CompanyMetrics, Long> {
    Optional<CompanyMetrics> findByCompanyId(String companyId);
}
