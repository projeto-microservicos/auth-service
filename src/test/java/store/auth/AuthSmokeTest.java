package store.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/** Verifies the wiring that is already done: HTTP, validation, secret checks, actuator, OpenAPI. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "management.server.port=0",
    "store.jwt.secret=test-secret-with-at-least-32-bytes-0123456789",
    "store.account-url=http://127.0.0.1:9"})
class AuthSmokeTest {

    @Value("${local.server.port}") int port;
    @Value("${local.management.port}") int managementPort;

    RestClient api() { return RestClient.create("http://localhost:" + port); }

    @Test
    void healthCheckAnswers200() {
        var response = api().get().uri("/auth/health-check").retrieve().toBodilessEntity();
        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void readinessProbeIsUpOnManagementPort() {
        var body = RestClient.create("http://localhost:" + managementPort)
            .get().uri("/actuator/health/readiness").retrieve().body(String.class);
        assertThat(body).contains("UP");
    }

    @Test
    void logoutExpiresTheHttpOnlyCookie() {
        var response = api().post().uri("/auth/logout").retrieve().toBodilessEntity();
        var cookie = response.getHeaders().getFirst("Set-Cookie");
        assertThat(cookie).startsWith("__store_jwt_token=").contains("Max-Age=0", "HttpOnly", "Secure");
    }

    @Test
    void invalidBodiesAreRejectedBeforeReachingTheService() {
        assertThatThrownBy(() -> api().post().uri("/auth/login").body(Map.of("email", "")).retrieve().toBodilessEntity())
            .isInstanceOf(HttpClientErrorException.BadRequest.class);
        assertThatThrownBy(() -> api().post().uri("/auth/solve").body(Map.of()).retrieve().toBodilessEntity())
            .isInstanceOf(HttpClientErrorException.BadRequest.class);
    }

    @Test
    void weakSecretIsRefusedAtStartup() {
        var weak = new AuthProperties("http://account:8080",
            new AuthProperties.Jwt("too-short", Duration.ofHours(1), "store-auth"),
            new AuthProperties.Cookie("__store_jwt_token", true, "Lax"));
        assertThatThrownBy(() -> new JwtService(weak)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void openApiDocumentsTheContract() {
        var docs = api().get().uri("/v3/api-docs").retrieve().body(String.class);
        assertThat(docs).contains("/auth/register", "/auth/login", "/auth/solve", "/auth/me");
    }

}
