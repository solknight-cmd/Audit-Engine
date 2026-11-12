package com.vault.audit_engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vault.audit_engine.config.VaultUserDetails;
import com.vault.audit_engine.model.User;
import com.vault.audit_engine.repository.UserRepository;
import com.vault.audit_engine.service.AccountService;
import com.vault.audit_engine.service.AccountService.LinkResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Companies, invitations, password links, roles and disabling, through the real API and sign-in. */
// No startup operator: a local .env may set VAULT_OPERATOR_EMAIL, and the bootstrap test needs a clean slate
@SpringBootTest(properties = "vault.operator-email=")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AccountFlowTest {

    private static final String PASSWORD = "a long enough passphrase";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountService accounts;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private static VaultUserDetails person(String email, String role, String tenantId) {
        return new VaultUserDetails(email, "unused", List.of(new SimpleGrantedAuthority(role)), tenantId, "Test Co", "Test Person");
    }

    private static final VaultUserDetails OPERATOR =
            person("operator@vaultcore.test", User.ROLE_PLATFORM_ADMIN, AccountService.OPERATOR_TENANT_ID);

    private static String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String tokenOf(String link) {
        return link.substring(link.indexOf("token=") + "token=".length());
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    /** Creates a company through the operator API and returns its id and the admin's invite link. */
    private JsonNode createCompany(String domain, String adminEmail) throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "companyName", unique("Acme "), "allowedDomain", domain == null ? "" : domain,
                "adminEmail", adminEmail, "adminName", "Ada Admin"));
        return json(mockMvc.perform(post("/api/v1/admin/companies").with(user(OPERATOR)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private void setPassword(String link, String password) throws Exception {
        mockMvc.perform(post("/api/v1/auth/set-password").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "token", tokenOf(link), "fullName", "Ada Admin", "password", password))))
                .andExpect(status().isOk());
    }

    @Test
    void operatorCreatesCompanyAndItsAdminAcceptsTheInvite() throws Exception {
        String domain = unique("acme") + ".test";
        String adminEmail = "ada@" + domain;
        JsonNode created = createCompany(domain, adminEmail);
        String link = created.at("/adminInvite/link").asText();

        // The public link page can look up who it's for
        mockMvc.perform(get("/api/v1/auth/token").param("token", tokenOf(link)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(adminEmail))
                .andExpect(jsonPath("$.purpose").value("INVITE"));

        // Before accepting, the account can't sign in
        mockMvc.perform(formLogin("/api/v1/auth/login").user(adminEmail).password(PASSWORD))
                .andExpect(redirectedUrl("/Login.html?error"));

        setPassword(link, PASSWORD);

        // Now it can, and a company admin lands on the dashboard
        mockMvc.perform(formLogin("/api/v1/auth/login").user(adminEmail.toUpperCase()).password(PASSWORD))
                .andExpect(redirectedUrl("/code.html"));

        // The link works only once
        mockMvc.perform(post("/api/v1/auth/set-password").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("token", tokenOf(link), "password", PASSWORD))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("expired or has already been used")));
    }

    @Test
    void companyAdminCanOnlyInviteAddressesAtTheCompanyDomain() throws Exception {
        String domain = unique("bank") + ".test";
        JsonNode created = createCompany(domain, "boss@" + domain);
        VaultUserDetails admin = person("boss@" + domain, User.ROLE_COMPANY_ADMIN, created.at("/company/companyId").asText());

        mockMvc.perform(post("/api/v1/team/users").with(user(admin)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"someone@gmail.com\",\"fullName\":\"Outsider\",\"role\":\"ROLE_AUDITOR\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("@" + domain)));

        mockMvc.perform(post("/api/v1/team/users").with(user(admin)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"auditor@" + domain + "\",\"fullName\":\"Ann Auditor\",\"role\":\"ROLE_AUDITOR\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.link").value(org.hamcrest.Matchers.containsString("/set-password.html?token=")));

        mockMvc.perform(get("/api/v1/team/users").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.email == 'auditor@" + domain + "')].status").value("INVITED"));
    }

    @Test
    void eachRoleOnlyReachesItsOwnArea() throws Exception {
        VaultUserDetails auditor = person("a@x.test", User.ROLE_AUDITOR, "SOME_CO");
        VaultUserDetails companyAdmin = person("b@x.test", User.ROLE_COMPANY_ADMIN, "SOME_CO");

        mockMvc.perform(get("/api/v1/team/users").with(user(auditor))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/companies").with(user(companyAdmin))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/audits/vault").with(user(OPERATOR))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/companies").with(user(OPERATOR))).andExpect(status().isOk());
    }

    @Test
    void companyAdminCantTouchAnotherCompanysUsers() throws Exception {
        JsonNode first = createCompany(null, unique("one") + "@first.test");
        JsonNode second = createCompany(null, unique("two") + "@second.test");
        Long secondsAdminId = userRepository.findByEmailIgnoreCase(second.at("/adminInvite/email").asText()).orElseThrow().getId();

        VaultUserDetails firstAdmin = person(first.at("/adminInvite/email").asText(), User.ROLE_COMPANY_ADMIN,
                first.at("/company/companyId").asText());
        mockMvc.perform(post("/api/v1/team/users/" + secondsAdminId + "/disable").with(user(firstAdmin)).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void disablingSignsSomeoneOutAndStopsSignIn() throws Exception {
        String domain = unique("gov") + ".test";
        String email = "officer@" + domain;
        JsonNode created = createCompany(domain, email);
        String companyId = created.at("/company/companyId").asText();
        setPassword(created.at("/adminInvite/link").asText(), PASSWORD);

        MockHttpSession session = (MockHttpSession) mockMvc.perform(formLogin("/api/v1/auth/login").user(email).password(PASSWORD))
                .andExpect(redirectedUrl("/code.html"))
                .andReturn().getRequest().getSession();
        mockMvc.perform(get("/api/v1/me").session(session)).andExpect(status().isOk());

        Long userId = userRepository.findByEmailIgnoreCase(email).orElseThrow().getId();
        mockMvc.perform(post("/api/v1/admin/companies/" + companyId + "/users/" + userId + "/disable")
                        .with(user(OPERATOR)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"));

        // The open session is ended, and signing in again is refused with its own message
        mockMvc.perform(get("/api/v1/me").session(session)).andExpect(status().isUnauthorized());
        mockMvc.perform(formLogin("/api/v1/auth/login").user(email).password(PASSWORD))
                .andExpect(redirectedUrl("/Login.html?disabled"));
    }

    @Test
    void resetLinkReplacesThePassword() throws Exception {
        String domain = unique("firm") + ".test";
        String email = "lead@" + domain;
        JsonNode created = createCompany(domain, email);
        String companyId = created.at("/company/companyId").asText();
        setPassword(created.at("/adminInvite/link").asText(), PASSWORD);
        Long userId = userRepository.findByEmailIgnoreCase(email).orElseThrow().getId();

        String resetLink = json(mockMvc.perform(post("/api/v1/admin/companies/" + companyId + "/users/" + userId + "/reset-link")
                        .with(user(OPERATOR)).with(csrf()))
                .andExpect(jsonPath("$.purpose").value("RESET"))
                .andReturn()).get("link").asText();
        setPassword(resetLink, "a different passphrase");

        mockMvc.perform(formLogin("/api/v1/auth/login").user(email).password(PASSWORD))
                .andExpect(redirectedUrl("/Login.html?error"));
        mockMvc.perform(formLogin("/api/v1/auth/login").user(email).password("a different passphrase"))
                .andExpect(redirectedUrl("/code.html"));
    }

    @Test
    void weakPasswordsAreRefusedWithAReason() throws Exception {
        JsonNode created = createCompany(null, unique("weak") + "@x.test");
        mockMvc.perform(post("/api/v1/auth/set-password").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "token", tokenOf(created.at("/adminInvite/link").asText()), "password", "short"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Use at least 10 characters."));
    }

    @Test
    void operatorBootstrapGivesALinkUntilTheOperatorHasSignedIn() throws Exception {
        LinkResult first = accounts.bootstrapOperator("owner@vaultcore.test", "http://localhost:8080");
        assertNotNull(first);
        assertTrue(first.link().startsWith("http://localhost:8080/set-password.html?token="));

        // Not used yet: a restart issues a fresh link (the old one stops working)
        LinkResult second = accounts.bootstrapOperator("owner@vaultcore.test", "http://localhost:8080");
        assertNotNull(second);
        mockMvc.perform(get("/api/v1/auth/token").param("token", tokenOf(first.link()))).andExpect(status().isBadRequest());

        setPassword(second.link(), PASSWORD);
        assertNull(accounts.bootstrapOperator("owner@vaultcore.test", "http://localhost:8080"));
        mockMvc.perform(formLogin("/api/v1/auth/login").user("owner@vaultcore.test").password(PASSWORD))
                .andExpect(redirectedUrl("/admin.html"));
    }

    @Test
    void peopleCanChangeTheirOwnPassword() throws Exception {
        String domain = unique("own") + ".test";
        String email = "me@" + domain;
        JsonNode created = createCompany(domain, email);
        setPassword(created.at("/adminInvite/link").asText(), PASSWORD);
        VaultUserDetails me = person(email, User.ROLE_COMPANY_ADMIN, created.at("/company/companyId").asText());

        mockMvc.perform(post("/api/v1/me/password").with(user(me)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"wrong one entirely\",\"newPassword\":\"brand new passphrase\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/me/password").with(user(me)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"brand new passphrase\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(formLogin("/api/v1/auth/login").user(email).password("brand new passphrase"))
                .andExpect(redirectedUrl("/code.html"));
    }
}
