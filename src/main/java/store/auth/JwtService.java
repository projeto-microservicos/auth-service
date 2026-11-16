package store.auth;

import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import store.auth.api.AccountOut;

/**
 * Issues and validates signed, expiring JWTs.
 */
@Service
public class JwtService {

    static final int MIN_SECRET_BYTES = 32; // 256 bits, required by HS256

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

    public String generate(AccountOut account) {
        Instant now = Instant.now();
        return Jwts.builder().subject(account.id()).issuer(settings.issuer())
            .issuedAt(Date.from(now)).notBefore(Date.from(now))
            .expiration(Date.from(now.plus(settings.duration())))
            .signWith(key).compact();
    }

    public String resolveAccountId(String token) {
        try {
            var claims = Jwts.parser().verifyWith(key).requireIssuer(settings.issuer()).build()
                .parseSignedClaims(token).getPayload();
            String id = claims.getSubject();
            if (id == null || id.isBlank() || claims.getExpiration() == null) {
                throw new IllegalArgumentException("Missing token claims");
            }
            return id;
        } catch (JwtException | IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token");
        }
    }

}
