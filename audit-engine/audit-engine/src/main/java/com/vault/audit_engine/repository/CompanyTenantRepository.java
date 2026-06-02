package com.vault.audit_engine.repository;

import com.vault.audit_engine.model.CompanyTenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CompanyTenantRepository extends JpaRepository<CompanyTenant, String> {
}
