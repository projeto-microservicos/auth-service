package store.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed view of the {@code store.*} block in application.yaml. */
@ConfigurationProperties("store")
public record AuthProperties(String accountUrl, Jwt jwt, Cookie cookie) {

    /** {@code duration}: access token lifetime; {@code refreshDuration}: refresh token lifetime. */
    public record Jwt(String secret, Duration duration, String issuer, Duration refreshDuration) {}

    /** {@code name}: access token cookie (sent everywhere); {@code refreshName}: refresh cookie (only /auth/refresh). */
    public record Cookie(String name, String refreshName, boolean secure, String sameSite) {}

}
