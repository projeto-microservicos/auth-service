package store.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed view of the {@code store.*} block in application.yaml. */
@ConfigurationProperties("store")
public record AuthProperties(String accountUrl, Jwt jwt, Cookie cookie) {

    public record Jwt(String secret, Duration duration, String issuer) {}

    public record Cookie(String name, boolean secure, String sameSite) {}

}
