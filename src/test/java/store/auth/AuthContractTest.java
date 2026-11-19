package store.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
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
import org.springframework.http.ResponseEntity;

/**
 * Executable specification of AuthService/JwtService against a stub account-service
 * (implemented on 17/11/2026, see the README's "Uso de IA").
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

    TokenOut tokens() {
        return api().post().uri("/auth/login").body(Map.of("email", "ana@example.com", "password", "s3cret-pass"))
            .retrieve().body(TokenOut.class);
    }

    ResponseEntity<TokenOut> refresh(Map<String, String> body) {
        return api().post().uri("/auth/refresh").body(body).retrieve().toEntity(TokenOut.class);
    }

    String solve(String token) {
        return api().post().uri("/auth/solve").body(Map.of("token", token)).retrieve().body(SolvedToken.class).idAccount();
    }

    @Test
    void registerCreatesTheAccount() {
        var response = api().post().uri("/auth/register")
            .body(Map.of("name", "Ana", "email", "ana@example.com", "password", "s3cret-pass"))
            .retrieve().toEntity(AccountOut.class);
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody().id()).isEqualTo("acc-1");
    }

    @Test
    void loginReturnsTokenInBodyAndHttpOnlyCookie() {
        var response = api().post().uri("/auth/login")
            .body(Map.of("email", "ana@example.com", "password", "s3cret-pass"))
            .retrieve().toEntity(TokenOut.class);
        assertThat(response.getBody().token()).isNotBlank();
        assertThat(response.getHeaders().getFirst("Set-Cookie"))
            .startsWith("__store_jwt_token=" + response.getBody().token()).contains("HttpOnly");

        assertThatThrownBy(() -> api().post().uri("/auth/login")
                .body(Map.of("email", "ana@example.com", "password", "wrong-pass")).retrieve().toBodilessEntity())
            .isInstanceOf(HttpClientErrorException.Unauthorized.class);
    }

    @Test
    void solveTurnsAValidTokenIntoTheAccountId() {
        String token = login("ana@example.com", "s3cret-pass");
        var solved = api().post().uri("/auth/solve").body(Map.of("token", token)).retrieve().body(SolvedToken.class);
        assertThat(solved.idAccount()).isEqualTo("acc-1");
    }

    @Test
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
    void signedAccessTokenWithoutExpirationIsRejected() {
        var key = io.jsonwebtoken.security.Keys.hmacShaKeyFor(
            "test-secret-with-at-least-32-bytes-0123456789".getBytes(StandardCharsets.UTF_8));
        var token = io.jsonwebtoken.Jwts.builder().subject("acc-1").issuer("store-auth")
            .claim("token_use", "access").signWith(key).compact();
        assertThatThrownBy(() -> solve(token))
            .isInstanceOf(HttpClientErrorException.Unauthorized.class);
    }

    @Test
    void meReturnsTheAccountOfTheGatewayHeader() {
        var me = api().get().uri("/auth/me").header("id-account", "acc-1").retrieve().body(AccountOut.class);
        assertThat(me.email()).isEqualTo("ana@example.com");
    }

    @Test
    void loginAnswersAnOAuth2StylePairAndTwoHttpOnlyCookies() {
        var response = api().post().uri("/auth/login")
            .body(Map.of("email", "ana@example.com", "password", "s3cret-pass"))
            .retrieve().toEntity(TokenOut.class);
        var body = response.getBody();
        assertThat(body.tokenType()).isEqualTo("Bearer");
        assertThat(body.expiresIn()).isEqualTo(Duration.ofMinutes(5).toSeconds());
        assertThat(body.refreshToken()).isNotBlank().isNotEqualTo(body.token());
        List<String> cookies = response.getHeaders().get("Set-Cookie");
        assertThat(cookies).anySatisfy(c -> assertThat(c).startsWith("__store_refresh_token=" + body.refreshToken())
            .contains("HttpOnly", "Path=/auth/refresh"));
    }

    @Test
    void refreshTokenFromBodyOrCookieGivesANewWorkingPair() {
        var first = tokens();

        var fromBody = refresh(Map.of("refreshToken", first.refreshToken())).getBody();
        assertThat(fromBody.token()).isNotEqualTo(first.token());
        assertThat(fromBody.refreshToken()).isNotEqualTo(first.refreshToken());
        assertThat(solve(fromBody.token())).isEqualTo("acc-1");

        var fromCookie = api().post().uri("/auth/refresh")
            .header("Cookie", "__store_refresh_token=" + fromBody.refreshToken())
            .retrieve().body(TokenOut.class);
        assertThat(solve(fromCookie.token())).isEqualTo("acc-1");
    }

    @Test
    void accessAndRefreshTokensAreNotInterchangeable() {
        var pair = tokens();
        assertThatThrownBy(() -> refresh(Map.of("refreshToken", pair.token())))
            .isInstanceOf(HttpClientErrorException.Unauthorized.class);
        assertThatThrownBy(() -> solve(pair.refreshToken()))
            .isInstanceOf(HttpClientErrorException.Unauthorized.class);
    }

    @Test
    void refreshWithoutTokenOrWithGarbageIs401() {
        assertThatThrownBy(() -> api().post().uri("/auth/refresh").retrieve().toBodilessEntity())
            .isInstanceOf(HttpClientErrorException.Unauthorized.class);
        assertThatThrownBy(() -> refresh(Map.of("refreshToken", "not-a-jwt")))
            .isInstanceOf(HttpClientErrorException.Unauthorized.class);
    }

    @Test
    void expiredAccessTokenIsRejected() {
        var expired = new JwtService(new AuthProperties("http://unused",
            new AuthProperties.Jwt("test-secret-with-at-least-32-bytes-0123456789", Duration.ofMinutes(-5),
                "store-auth", Duration.ofMinutes(-5)),
            new AuthProperties.Cookie("a", "b", true, "Lax")))
            .issue(new AccountOut("acc-1", "Ana", "ana@example.com"));
        assertThatThrownBy(() -> solve(expired.access()))
            .isInstanceOf(HttpClientErrorException.Unauthorized.class);
        assertThatThrownBy(() -> refresh(Map.of("refreshToken", expired.refresh())))
            .isInstanceOf(HttpClientErrorException.Unauthorized.class);
    }

    @Test
    void refreshOfADeletedAccountIs401() {
        var foreign = new JwtService(new AuthProperties("http://unused",
            new AuthProperties.Jwt("test-secret-with-at-least-32-bytes-0123456789", Duration.ofMinutes(5),
                "store-auth", Duration.ofMinutes(5)),
            new AuthProperties.Cookie("a", "b", true, "Lax")))
            .issue(new AccountOut("deleted-account", "Bob", "bob@example.com"));
        assertThatThrownBy(() -> refresh(Map.of("refreshToken", foreign.refresh())))
            .isInstanceOf(HttpClientErrorException.Unauthorized.class);
    }

}
