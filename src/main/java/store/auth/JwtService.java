package store.auth;

import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import store.auth.api.AccountOut;

/**
 * Issues and validates the JWTs. Key loading is done (fails fast on a weak secret);
 * token generation and validation are yours.
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
        // TODO(auth): build a signed token with Jwts.builder():
        //  - id/subject = account.id(), issuer = settings.issuer()
        //  - issuedAt/notBefore = now, expiration = now + settings.duration()
        //  - optional claims: email, name. Sign with .signWith(key).
        throw todo("generate JWT");
    }

    public String resolveAccountId(String token) {
        // TODO(auth): Jwts.parser().verifyWith(key).requireIssuer(settings.issuer()).build()
        //  .parseSignedClaims(token) and return the account id.
        //  Any JwtException (bad signature, expired, malformed) -> 401 UNAUTHORIZED.
        throw todo("validate JWT");
    }

    private static ResponseStatusException todo(String what) {
        return new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "TODO: " + what);
    }

}
