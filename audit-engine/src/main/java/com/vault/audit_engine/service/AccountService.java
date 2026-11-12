package com.vault.audit_engine.service;

import com.vault.audit_engine.model.AccountToken;
import com.vault.audit_engine.model.AccountToken.Purpose;
import com.vault.audit_engine.model.CompanyTenant;
import com.vault.audit_engine.model.User;
import com.vault.audit_engine.model.User.Status;
import com.vault.audit_engine.repository.AccountTokenRepository;
import com.vault.audit_engine.repository.CompanyTenantRepository;
import com.vault.audit_engine.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Companies, users and their passwords. There is no email service, so invitations and password
 * resets produce a one-time link that the admin passes on; the person then chooses their own password.
 */
@Service
public class AccountService {

    private static final Logger logger = LoggerFactory.getLogger(AccountService.class);

    /** Tenant that holds the operator's own accounts; it isn't a client company. */
    public static final String OPERATOR_TENANT_ID = "VAULT_CORE_OPERATOR";

    private static final Duration INVITE_VALID_FOR = Duration.ofDays(7);
    private static final Duration RESET_VALID_FOR = Duration.ofHours(24);
    private static final int MIN_PASSWORD_LENGTH = 10;
    private static final int MAX_PASSWORD_BYTES = 72; // BCrypt ignores anything longer
    private static final Set<String> INVITABLE_ROLES = Set.of(User.ROLE_AUDITOR, User.ROLE_COMPANY_ADMIN);

    private final UserRepository userRepository;
    private final CompanyTenantRepository tenantRepository;
    private final AccountTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionRegistry sessionRegistry;
    private final SecureRandom random = new SecureRandom();

    public AccountService(UserRepository userRepository, CompanyTenantRepository tenantRepository,
                          AccountTokenRepository tokenRepository, PasswordEncoder passwordEncoder,
                          SessionRegistry sessionRegistry) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.sessionRegistry = sessionRegistry;
    }

    // What the API returns; never includes password hashes or token secrets other than a fresh link

    public record UserSummary(Long id, String email, String fullName, String role, String status,
                              Instant createdAt, Instant lastLoginAt) {
        static UserSummary of(User user) {
            return new UserSummary(user.getId(), user.getEmail(), user.getFullName(), user.getRole(),
                    user.getStatus().name(), user.getCreatedAt(), user.getLastLoginAt());
        }
    }

    public record CompanySummary(String companyId, String companyName, String allowedDomain, long users) {}

    /** A freshly created one-time link. The link is shown once and can't be looked up again later. */
    public record LinkResult(String email, String purpose, String link, Instant expiresAt) {}

    public record CompanyCreated(CompanySummary company, LinkResult adminInvite) {}

    public record TokenInfo(String email, String fullName, String companyName, String purpose) {}

    // ---- Companies (operator only) ----

    @Transactional
    public CompanyCreated createCompany(String companyName, String allowedDomain, String adminEmail,
                                        String adminName, String createdBy, String baseUrl) {
        if (companyName == null || companyName.isBlank()) {
            throw AccountException.badRequest("Company name is required.");
        }
        CompanyTenant tenant = new CompanyTenant(newTenantId(companyName), companyName.trim(), allowedDomain);
        if (tenant.getAllowedDomain() != null) {
            if (!tenant.getAllowedDomain().matches("[a-z0-9.-]+\\.[a-z]{2,}")) {
                throw AccountException.badRequest("Email domain should look like \"company.com\".");
            }
            boolean domainTaken = tenantRepository.findAll().stream()
                    .anyMatch(t -> tenant.getAllowedDomain().equals(t.getAllowedDomain()));
            if (domainTaken) {
                throw AccountException.conflict("Another company already uses the domain " + tenant.getAllowedDomain() + ".");
            }
        }
        tenantRepository.save(tenant);
        LinkResult invite = invite(tenant.getTenantId(), adminEmail, adminName, User.ROLE_COMPANY_ADMIN, createdBy, baseUrl);
        logger.info("Company {} created by {}", tenant.getTenantId(), createdBy);
        return new CompanyCreated(summarize(tenant), invite);
    }

    public List<CompanySummary> listCompanies() {
        return tenantRepository.findAll().stream()
                .filter(t -> !OPERATOR_TENANT_ID.equals(t.getTenantId()))
                .map(this::summarize)
                .sorted((a, b) -> a.companyName().compareToIgnoreCase(b.companyName()))
                .toList();
    }

    public void requireCompany(String tenantId) {
        if (!tenantRepository.existsById(tenantId)) {
            throw AccountException.notFound("No such company.");
        }
    }

    // ---- Users of one company (company admin for their own, operator for any) ----

    public List<UserSummary> listUsers(String tenantId) {
        return userRepository.findByTenantTenantIdOrderByCreatedAtAsc(tenantId).stream().map(UserSummary::of).toList();
    }

    @Transactional
    public LinkResult invite(String tenantId, String email, String fullName, String role, String createdBy, String baseUrl) {
        CompanyTenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> AccountException.notFound("No such company."));
        String normalizedEmail = normalizeEmail(email);
        if (fullName == null || fullName.isBlank()) {
            throw AccountException.badRequest("Name is required.");
        }
        if (!INVITABLE_ROLES.contains(role)) {
            throw AccountException.badRequest("Role must be auditor or company admin.");
        }
        if (tenant.getAllowedDomain() != null && !normalizedEmail.endsWith("@" + tenant.getAllowedDomain())) {
            throw AccountException.badRequest("Only addresses at @" + tenant.getAllowedDomain() + " can join " + tenant.getCompanyName() + ".");
        }

        User user = userRepository.findByEmailIgnoreCase(normalizedEmail).orElse(null);
        if (user == null) {
            user = new User();
            user.setEmail(normalizedEmail);
            user.setTenant(tenant);
            user.setStatus(Status.INVITED);
        } else if (!user.getTenant().getTenantId().equals(tenantId) || user.getStatus() != Status.INVITED) {
            // Re-inviting someone who hasn't accepted yet just issues a new link
            throw AccountException.conflict(normalizedEmail + " already has an account.");
        }
        user.setFullName(fullName.trim());
        user.setRole(role);
        userRepository.save(user);
        logger.info("{} invited {} to {} as {}", createdBy, normalizedEmail, tenantId, role);
        return issueLink(user, Purpose.INVITE, createdBy, baseUrl);
    }

    /** A link to set a new password; for someone who never accepted their invitation, a fresh invitation. */
    @Transactional
    public LinkResult createResetLink(String tenantId, Long userId, String createdBy, String baseUrl) {
        User user = userInTenant(tenantId, userId);
        if (user.getStatus() == Status.DISABLED) {
            throw AccountException.badRequest("Enable this account before resetting its password.");
        }
        Purpose purpose = user.getStatus() == Status.INVITED ? Purpose.INVITE : Purpose.RESET;
        logger.info("{} created a {} link for {}", createdBy, purpose, user.getEmail());
        return issueLink(user, purpose, createdBy, baseUrl);
    }

    @Transactional
    public UserSummary setEnabled(String tenantId, Long userId, boolean enabled, String actingEmail) {
        User user = userInTenant(tenantId, userId);
        if (user.getEmail().equalsIgnoreCase(actingEmail)) {
            throw AccountException.badRequest("You can't disable or enable your own account.");
        }
        if (enabled) {
            if (user.getStatus() == Status.DISABLED) {
                user.setStatus(user.getPassword() == null ? Status.INVITED : Status.ACTIVE);
            }
        } else {
            user.setStatus(Status.DISABLED);
            voidOpenLinks(user);
            expireSessions(user.getEmail());
        }
        userRepository.save(user);
        logger.info("{} {} {}", actingEmail, enabled ? "enabled" : "disabled", user.getEmail());
        return UserSummary.of(user);
    }

    // ---- One-time links (no sign-in needed) ----

    public TokenInfo describeToken(String rawToken) {
        AccountToken token = usableToken(rawToken);
        User user = token.getUser();
        return new TokenInfo(user.getEmail(), user.getFullName(), user.getTenant().getCompanyName(), token.getPurpose().name());
    }

    @Transactional
    public void setPasswordWithToken(String rawToken, String fullName, String password) {
        AccountToken token = usableToken(rawToken);
        User user = token.getUser();
        if (user.getStatus() == Status.DISABLED) {
            throw AccountException.badRequest("This account has been disabled. Contact your administrator.");
        }
        checkPassword(password, user.getEmail());
        if (fullName != null && !fullName.isBlank()) {
            user.setFullName(fullName.trim());
        }
        user.setPassword(passwordEncoder.encode(password));
        user.setStatus(Status.ACTIVE);
        userRepository.save(user);
        token.markUsed(Instant.now());
        tokenRepository.save(token);
        voidOpenLinks(user);
        // After a reset, anyone still signed in with the old password is signed out
        expireSessions(user.getEmail());
        logger.info("Password set for {} using a {} link", user.getEmail(), token.getPurpose());
    }

    // ---- The signed-in user's own account ----

    @Transactional
    public void changeOwnPassword(String email, String currentPassword, String newPassword) {
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> AccountException.notFound("Account not found."));
        if (currentPassword == null || user.getPassword() == null
                || !passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw AccountException.badRequest("Your current password is incorrect.");
        }
        checkPassword(newPassword, user.getEmail());
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        logger.info("{} changed their password", user.getEmail());
    }

    @Transactional
    public void recordLogin(String email) {
        userRepository.findByEmailIgnoreCase(email).ifPresent(user -> {
            user.setLastLoginAt(Instant.now());
            userRepository.save(user);
        });
    }

    // ---- Operator bootstrap ----

    /**
     * Creates the first operator account if there is none yet, and returns its invitation link.
     * If the operator was created but never set a password (the link expired), returns a fresh link.
     * Returns null once an operator can sign in.
     */
    @Transactional
    public LinkResult bootstrapOperator(String email, String baseUrl) {
        List<User> operators = userRepository.findByRole(User.ROLE_PLATFORM_ADMIN);
        if (operators.stream().anyMatch(u -> u.getStatus() == Status.ACTIVE)) {
            return null;
        }
        if (!operators.isEmpty()) {
            return issueLink(operators.get(0), Purpose.INVITE, "system", baseUrl);
        }
        String normalizedEmail = normalizeEmail(email);
        if (userRepository.findByEmailIgnoreCase(normalizedEmail).isPresent()) {
            throw AccountException.conflict(normalizedEmail + " already has an account in a company; use another address for the operator.");
        }
        CompanyTenant operatorTenant = tenantRepository.findById(OPERATOR_TENANT_ID)
                .orElseGet(() -> tenantRepository.save(new CompanyTenant(OPERATOR_TENANT_ID, "Vault Core (operator)", null)));
        User operator = new User();
        operator.setEmail(normalizedEmail);
        operator.setFullName("Vault Core Operator");
        operator.setRole(User.ROLE_PLATFORM_ADMIN);
        operator.setTenant(operatorTenant);
        operator.setStatus(Status.INVITED);
        userRepository.save(operator);
        return issueLink(operator, Purpose.INVITE, "system", baseUrl);
    }

    // ---- Helpers ----

    static void checkPassword(String password, String email) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw AccountException.badRequest("Use at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw AccountException.badRequest("Use at most " + MAX_PASSWORD_BYTES + " characters.");
        }
        String emailName = email == null ? "" : email.split("@")[0].toLowerCase(Locale.ROOT);
        if (emailName.length() >= 4 && password.toLowerCase(Locale.ROOT).contains(emailName)) {
            throw AccountException.badRequest("Don't use your email name in your password.");
        }
    }

    private LinkResult issueLink(User user, Purpose purpose, String createdBy, String baseUrl) {
        voidOpenLinks(user); // Only the newest link works
        byte[] secret = new byte[32];
        random.nextBytes(secret);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        Instant expiresAt = Instant.now().plus(purpose == Purpose.INVITE ? INVITE_VALID_FOR : RESET_VALID_FOR);
        tokenRepository.save(new AccountToken(sha256(rawToken), user, purpose, createdBy, expiresAt));
        String link = trimTrailingSlash(baseUrl) + "/set-password.html?token=" + rawToken;
        return new LinkResult(user.getEmail(), purpose.name(), link, expiresAt);
    }

    private void voidOpenLinks(User user) {
        if (user.getId() == null) {
            return;
        }
        Instant now = Instant.now();
        List<AccountToken> open = tokenRepository.findByUserIdAndUsedAtIsNull(user.getId());
        open.forEach(token -> token.markUsed(now));
        tokenRepository.saveAll(open);
    }

    private AccountToken usableToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw AccountException.badRequest("This link is missing its code.");
        }
        return tokenRepository.findByTokenHash(sha256(rawToken.trim()))
                .filter(token -> token.isUsable(Instant.now()))
                .orElseThrow(() -> AccountException.badRequest(
                        "This link has expired or has already been used. Ask your administrator for a new one."));
    }

    private User userInTenant(String tenantId, Long userId) {
        return userRepository.findById(userId)
                .filter(user -> user.getTenant().getTenantId().equals(tenantId))
                .orElseThrow(() -> AccountException.notFound("No such user in this company."));
    }

    /** Signs the person out of every browser they're signed in on. */
    private void expireSessions(String email) {
        for (Object principal : sessionRegistry.getAllPrincipals()) {
            if (principal instanceof UserDetails details && details.getUsername().equalsIgnoreCase(email)) {
                sessionRegistry.getAllSessions(principal, false).forEach(SessionInformation::expireNow);
            }
        }
    }

    private CompanySummary summarize(CompanyTenant tenant) {
        return new CompanySummary(tenant.getTenantId(), tenant.getCompanyName(), tenant.getAllowedDomain(),
                userRepository.countByTenantTenantId(tenant.getTenantId()));
    }

    /** "Acme Holdings Ltd" becomes e.g. "ACME_HOLDINGS_LTD_7F3A". */
    private String newTenantId(String companyName) {
        String base = companyName.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_|_$", "");
        base = base.isEmpty() ? "COMPANY" : base.substring(0, Math.min(base.length(), 40));
        String id;
        do {
            id = base + "_" + HexFormat.of().withUpperCase().formatHex(new byte[]{(byte) random.nextInt(), (byte) random.nextInt()});
        } while (tenantRepository.existsById(id));
        return id;
    }

    private static String normalizeEmail(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            throw AccountException.badRequest("Enter a valid email address.");
        }
        return normalized;
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available on the JVM", e);
        }
    }
}
