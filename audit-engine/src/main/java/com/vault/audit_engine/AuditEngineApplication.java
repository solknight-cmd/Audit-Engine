package com.vault.audit_engine;

import com.vault.audit_engine.model.CompanyTenant;
import com.vault.audit_engine.model.User;

import com.vault.audit_engine.model.CompanyTenant;
import com.vault.audit_engine.model.User;
import com.vault.audit_engine.repository.CompanyTenantRepository;
import com.vault.audit_engine.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootApplication
public class AuditEngineApplication {

	public static void main(String[] args) {
		SpringApplication.run(AuditEngineApplication.class, args);
	}

	/**
	 * Automatically seeds clean multi-tenant demo data on startup using repositories.
	 * This bypasses lifecycle proxy issues and manages database transactions safely.
	 */
	@Bean
	public CommandLineRunner seedDemoData(UserRepository userRepo,
										  CompanyTenantRepository tenantRepo,
										  PasswordEncoder encoder) {
		return args -> {
			// Check if the user database is currently empty before trying to seed
			if (userRepo.count() == 0) {
				System.out.println("⏳ Detecting empty security schema... Initializing fresh B2B demo profile.");

				// 1. Programmatically save the corporate tenant wrappers via safe Spring Repositories
				CompanyTenant globalTenant = new CompanyTenant("GLOBAL_CAPITAL_CORP_001", "Global Capital Corp", "globalcapital.com");
				tenantRepo.save(globalTenant);

				CompanyTenant vanguardTenant = new CompanyTenant("VANGUARD_EQUITIES_LTD", "Vanguard Equities Ltd", "vanguard.com");
				tenantRepo.save(vanguardTenant);

				// 2. Programmatically generate the lead auditor user profile
				User demoUser = new User();
				demoUser.setFullName("Alex Auditore");
				demoUser.setEmail("alex@globalcapital.com");
				demoUser.setRole("ROLE_AUDITOR");
				demoUser.setTenant(globalTenant);

				// Cryptographically salt the text using BCrypt live on your machine instance
				demoUser.setPassword(encoder.encode("secure2026"));

				userRepo.save(demoUser);

				System.out.println("=================================================");
				System.out.println("✅ FRESH B2B DEMO PROFILE SUCCESSFULLY SEEDED:");
				System.out.println("👉 Corporate Email: alex@globalcapital.com");
				System.out.println("👉 Secure Password: secure2026");
				System.out.println("=================================================");
			} else {
				System.out.println("✅ Persistent multi-tenant user database detected. Skipping seeding cycle.");
			}
		};
	}
}
