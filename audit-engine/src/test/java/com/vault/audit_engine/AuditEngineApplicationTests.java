package com.vault.audit_engine;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AuditEngineApplicationTests {

	@Test
	void contextLoads() {
	}

}
