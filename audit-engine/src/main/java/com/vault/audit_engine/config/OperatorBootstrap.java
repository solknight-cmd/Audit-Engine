package com.vault.audit_engine.config;

import com.vault.audit_engine.service.AccountService;
import com.vault.audit_engine.service.AccountService.LinkResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Creates the first Vault Core operator from VAULT_OPERATOR_EMAIL and logs a one-time link to
 * choose its password. Does nothing once an operator has signed in. The link is printed only to
 * the server log, so only someone with access to the server can use it.
 */
@Component
public class OperatorBootstrap implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(OperatorBootstrap.class);

    private final AccountService accounts;
    private final String operatorEmail;
    private final String publicUrl;

    public OperatorBootstrap(AccountService accounts,
                             @Value("${vault.operator-email:}") String operatorEmail,
                             @Value("${vault.public-url:http://localhost:8080}") String publicUrl) {
        this.accounts = accounts;
        this.operatorEmail = operatorEmail;
        this.publicUrl = publicUrl;
    }

    @Override
    public void run(String... args) {
        if (operatorEmail == null || operatorEmail.isBlank()) {
            return;
        }
        LinkResult link = accounts.bootstrapOperator(operatorEmail, publicUrl);
        if (link != null) {
            logger.warn("""

                    =================================================================
                    Vault Core operator account: {}
                    Open this link to choose its password (works once, expires {}):
                    {}
                    =================================================================""", link.email(), link.expiresAt(), link.link());
        }
    }
}
