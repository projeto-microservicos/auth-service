package store.auth.api;

/**
 * Response of {@code POST /auth/login}. The same token also goes in an HttpOnly cookie;
 * the body copy makes scripted clients (load tests, curl) easier to write.
 */
public record TokenOut(String token) {}
