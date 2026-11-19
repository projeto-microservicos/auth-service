package store.auth;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import store.auth.api.AccountOut;

/**
 * Issues and validates the JWTs (HS256, OAuth2-style pair).
 *
 * <ul>
 *   <li><b>access</b> token: short-lived ({@code JWT_DURATION}, 15 min by default); sent on every request
 *       (cookie or {@code Authorization: Bearer}) and resolved by the gateway through {@code /auth/solve}.</li>
 *   <li><b>refresh</b> token: long-lived ({@code JWT_REFRESH_DURATION}, 7 days by default); only accepted by
 *       {@code /auth/refresh}, which answers a new pair.</li>
 * </ul>
 * The {@value #TOKEN_USE} claim keeps the two apart: a refresh token never authenticates a request and an
 * access token never refreshes. Every token has a random {@code jti}, ready for a future deny-list.
 */
@Service
public class JwtService {

    static final int MIN_SECRET_BYTES = 32; // 256 bits, required by HS256
    static final String TOKEN_USE = "token_use";
    static final String ACCESS = "access";
    static final String REFRESH = "refresh";
    /** Tolerated clock difference between pods when checking exp/nbf. */
    static final long CLOCK_SKEW_SECONDS = 30;

    private final SecretKey key;
    private final AuthProperties.Jwt settings;

    public JwtService(AuthProperties properties) {
        this.settings = properties.jwt();
        String secret = settings.secret() == null ? "" : settings.secret();
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                "JWT_SECRET must have at least " + MIN_SECRET_BYTES + " bytes; got " + bytes.length);
        }
        this.key = Keys.hmacShaKeyFor(bytes);
    }

    /** A new access + refresh pair for the account. */
    public Tokens issue(AccountOut account) {
        Instant now = Instant.now();
        String access = Jwts.builder()
            .id(UUID.randomUUID().toString())
            .subject(account.id())
            .issuer(settings.issuer())
            .issuedAt(Date.from(now))
            .notBefore(Date.from(now))
            .expiration(Date.from(now.plus(settings.duration())))
            .claim(TOKEN_USE, ACCESS)
            .claim("email", account.email())
            .claim("name", account.name())
            .signWith(key)
            .compact();
        // The refresh token carries no personal data: it only names the account to reload.
        String refresh = Jwts.builder()
            .id(UUID.randomUUID().toString())
            .subject(account.id())
            .issuer(settings.issuer())
            .issuedAt(Date.from(now))
            .notBefore(Date.from(now))
            .expiration(Date.from(now.plus(settings.refreshDuration())))
            .claim(TOKEN_USE, REFRESH)
            .signWith(key)
            .compact();
        return new Tokens(access, refresh, settings.duration(), settings.refreshDuration());
    }

    /** Account id of a valid access token; anything else (refresh token, expired, tampered) is 401. */
    public String resolveAccountId(String token) {
        return subject(token, ACCESS);
    }

    /** Account id of a valid refresh token; anything else is 401. */
    public String resolveRefresh(String token) {
        return subject(token, REFRESH);
    }

    private String subject(String token, String use) {
        try {
            var claims = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(settings.issuer())
                .require(TOKEN_USE, use)
                .clockSkewSeconds(CLOCK_SKEW_SECONDS)
                .build()
                .parseSignedClaims(token)
                .getPayload();
            String subject = claims.getSubject();
            if (subject == null || subject.isBlank() || claims.getExpiration() == null) {
                throw unauthorized();
            }
            return subject;
        } catch (JwtException | IllegalArgumentException e) {
            throw unauthorized();
        }
    }

    private static ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid or expired token");
    }

    /** Both tokens of a login or refresh, with their lifetimes (for cookies and {@code expiresIn}). */
    public record Tokens(String access, String refresh, Duration accessDuration, Duration refreshDuration) {}

}
