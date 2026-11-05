package com.vault.audit_engine;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The browser login flow over real HTTP, as the static Login.html page performs it.
 * (MockMvc's csrf() helper changes how tokens are stored, so this can't live in the MockMvc tests.)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class LoginFlowTest {

    @LocalServerPort
    private int port;

    // Doesn't follow redirects, so we see the login response itself
    private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    @Test
    void loginNeedsTheCsrfTokenFromTheCookie() throws Exception {
        // 1. Loading the login page hands out the token cookie
        HttpResponse<String> page = client.send(
                HttpRequest.newBuilder(url("/Login.html")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, page.statusCode());
        String token = page.headers().allValues("Set-Cookie").stream()
                .filter(h -> h.startsWith("XSRF-TOKEN="))
                .map(h -> h.substring("XSRF-TOKEN=".length(), h.indexOf(';')))
                .findFirst()
                .orElse(null);
        assertNotNull(token, "Login page must set the XSRF-TOKEN cookie");

        // 2. Without the token the login is refused
        assertEquals(403, login(null).statusCode());

        // 3. With the cookie and matching _csrf field (what Login.html sends), the demo user gets in
        HttpResponse<String> ok = login(token);
        assertEquals(302, ok.statusCode());
        assertTrue(ok.headers().firstValue("Location").orElse("").endsWith("/code.html"));
    }

    private HttpResponse<String> login(String token) throws Exception {
        // Demo seed credentials
        String form = "username=" + enc("alex@globalcapital.com") + "&password=" + enc("secure2026")
                + (token == null ? "" : "&_csrf=" + enc(token));
        HttpRequest.Builder request = HttpRequest.newBuilder(url("/api/v1/auth/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form));
        if (token != null) {
            request.header("Cookie", "XSRF-TOKEN=" + token);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI url(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
