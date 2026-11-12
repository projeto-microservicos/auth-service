package store.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import store.auth.api.AccountOut;
import store.auth.api.SolvedToken;
import store.auth.api.TokenOut;

/**
 * Executable specification of AuthService/JwtService against a stub account-service.
 * Remove @Disabled as you implement each TODO(auth).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "management.server.port=0",
    "store.jwt.secret=test-secret-with-at-least-32-bytes-0123456789",
    "store.jwt.duration=PT5M"})
class AuthContractTest {

    static final String ACCOUNT = "{\"id\":\"acc-1\",\"name\":\"Ana\",\"email\":\"ana@example.com\"}";
    static HttpServer accountStub;

    @Value("${local.server.port}") int port;

    /** Stub account-service: one valid user (ana@example.com / s3cret-pass). */
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws IOException {
        accountStub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        accountStub.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            int status;
            String response;
            if (path.equals("/accounts") && exchange.getRequestMethod().equals("POST")) {
                status = 201; response = ACCOUNT;
            } else if (path.equals("/accounts/login")) {
                boolean ok = body.contains("ana@example.com") && body.contains("s3cret-pass");
                status = ok ? 200 : 401; response = ok ? ACCOUNT : "{}";
            } else if (path.equals("/accounts/acc-1")) {
                status = 200; response = ACCOUNT;
            } else {
                status = 404; response = "{}";
            }
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        accountStub.start();
        registry.add("store.account-url", () -> "http://127.0.0.1:" + accountStub.getAddress().getPort());
    }

    @AfterAll
    static void stop() { accountStub.stop(0); }

    RestClient api() { return RestClient.create("http://localhost:" + port); }

    String login(String email, String password) {
        return api().post().uri("/auth/login").body(Map.of("email", email, "password", password))
            .retrieve().body(TokenOut.class).token();
    }

    @Test
    @Disabled("TODO(auth): implement AuthService.register")
    void registerCreatesTheAccount() {
        var response = api().post().uri("/auth/register")
            .body(Map.of("name", "Ana", "email", "ana@example.com", "password", "s3cret-pass"))
            .retrieve().toEntity(AccountOut.class);
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody().id()).isEqualTo("acc-1");
    }

    @Test
    @Disabled("TODO(auth): implement AuthService.login and JwtService.generate")
    void loginReturnsTokenInBodyAndHttpOnlyCookie() {
        var response = api().post().uri("/auth/login")
            .body(Map.of("email", "ana@example.com", "password", "s3cret-pass"))
            .retrieve().toEntity(TokenOut.class);
        assertThat(response.getBody().token()).isNotBlank();
        assertThat(response.getHeaders().getFirst("Set-Cookie"))
            .startsWith("__store_jwt_token=" + response.getBody().token()).contains("HttpOnly");

        assertThatThrownBy(() -> login("ana@example.com", "wrong-pass"))
            .isInstanceOf(HttpClientErrorException.Unauthorized.class);
    }

    @Test
    @Disabled("TODO(auth): implement AuthService.solve and JwtService.resolveAccountId")
    void solveTurnsAValidTokenIntoTheAccountId() {
        String token = login("ana@example.com", "s3cret-pass");
        var solved = api().post().uri("/auth/solve").body(Map.of("token", token)).retrieve().body(SolvedToken.class);
        assertThat(solved.idAccount()).isEqualTo("acc-1");
    }

    @Test
    @Disabled("TODO(auth): implement JwtService.resolveAccountId")
    void solveRejectsTamperedAndGarbageTokens() {
        String token = login("ana@example.com", "s3cret-pass");
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("A") ? "BB" : "AA");
        for (String bad : new String[] {tampered, "not-a-jwt"}) {
            assertThatThrownBy(() -> api().post().uri("/auth/solve").body(Map.of("token", bad))
                    .retrieve().toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.Unauthorized.class);
        }
    }

    @Test
    @Disabled("TODO(auth): implement AuthService.me")
    void meReturnsTheAccountOfTheGatewayHeader() {
        var me = api().get().uri("/auth/me").header("id-account", "acc-1").retrieve().body(AccountOut.class);
        assertThat(me.email()).isEqualTo("ana@example.com");
    }

}
