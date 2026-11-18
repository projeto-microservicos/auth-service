package store.auth.api;

/**
 * Optional body of {@code POST /auth/refresh}: {"refreshToken": "..."}, for scripted clients.
 * Browsers send the HttpOnly refresh cookie instead, and the body may be absent.
 */
public record RefreshIn(String refreshToken) {}
