package store.auth.api;

/**
 * Response of {@code POST /auth/login} and {@code POST /auth/refresh}, OAuth2-style.
 *
 * <p>{@code token} is the access token (same name as before, so existing clients keep working);
 * {@code expiresIn} is its lifetime in seconds. The same tokens also go in HttpOnly cookies; the body
 * copy makes scripted clients (load tests, curl) easier to write.
 */
public record TokenOut(String token, String refreshToken, String tokenType, long expiresIn) {

    public static TokenOut bearer(String token, String refreshToken, long expiresIn) {
        return new TokenOut(token, refreshToken, "Bearer", expiresIn);
    }

}
