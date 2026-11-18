package store.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.WebUtils;
import store.auth.api.AccountOut;
import store.auth.api.LoginIn;
import store.auth.api.RefreshIn;
import store.auth.api.RegisterIn;
import store.auth.api.SolvedToken;
import store.auth.api.TokenIn;
import store.auth.api.TokenOut;

/** HTTP contract of the auth service (paths from the course's AuthController). */
@RestController
@RequestMapping("/auth")
public class AuthResource {

    private final AuthService service;
    private final AuthProperties properties;

    public AuthResource(AuthService service, AuthProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @PostMapping("/register")
    public ResponseEntity<AccountOut> register(@Valid @RequestBody RegisterIn in) {
        AccountOut created = service.register(in);
        return ResponseEntity.created(URI.create("/accounts/" + created.id())).body(created);
    }

    @PostMapping("/login")
    public ResponseEntity<TokenOut> login(@Valid @RequestBody LoginIn in) {
        return tokens(service.login(in));
    }

    /**
     * New token pair from the refresh token: the HttpOnly cookie (browsers) or {"refreshToken"} in the body
     * (scripts). Open route in the gateway: the access token may already have expired.
     */
    @PostMapping("/refresh")
    public ResponseEntity<TokenOut> refresh(@RequestBody(required = false) RefreshIn in, HttpServletRequest request) {
        String token = in != null && in.refreshToken() != null && !in.refreshToken().isBlank()
            ? in.refreshToken()
            : cookieValue(request, properties.cookie().refreshName());
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "missing refresh token");
        }
        return tokens(service.refresh(token.trim()));
    }

    /** Expires both cookies. Tokens are stateless: an access token already copied stays valid until it expires. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent()
            .header(HttpHeaders.SET_COOKIE, tokenCookie("", Duration.ZERO).toString())
            .header(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO).toString())
            .build();
    }

    /** id-account is set by the gateway after validating the token; never by the client. */
    @GetMapping("/me")
    public AccountOut me(@RequestHeader("id-account") String idAccount) {
        return service.me(idAccount);
    }

    /** Internal: used by the gateway to turn a token into an account id. Not routed publicly. */
    @PostMapping("/solve")
    public SolvedToken solve(@Valid @RequestBody TokenIn in) {
        return service.solve(in.token());
    }

    @GetMapping("/health-check")
    public ResponseEntity<Void> healthCheck() {
        return ResponseEntity.ok().build();
    }

    private ResponseEntity<TokenOut> tokens(JwtService.Tokens tokens) {
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, tokenCookie(tokens.access(), tokens.accessDuration()).toString())
            .header(HttpHeaders.SET_COOKIE, refreshCookie(tokens.refresh(), tokens.refreshDuration()).toString())
            .body(TokenOut.bearer(tokens.access(), tokens.refresh(), tokens.accessDuration().toSeconds()));
    }

    private ResponseCookie tokenCookie(String value, Duration maxAge) {
        return cookie(properties.cookie().name(), value, "/", maxAge);
    }

    /** Only sent back on /auth/refresh, so the long-lived token does not travel on every request. */
    private ResponseCookie refreshCookie(String value, Duration maxAge) {
        return cookie(properties.cookie().refreshName(), value, "/auth/refresh", maxAge);
    }

    private ResponseCookie cookie(String name, String value, String path, Duration maxAge) {
        var cookie = properties.cookie();
        return ResponseCookie.from(name, value)
            .httpOnly(true)               // not readable from JavaScript (XSS)
            .secure(cookie.secure())      // HTTPS only in production
            .sameSite(cookie.sameSite())  // CSRF protection
            .path(path)
            .maxAge(maxAge)
            .build();
    }

    private static String cookieValue(HttpServletRequest request, String name) {
        var cookie = WebUtils.getCookie(request, name);
        return cookie == null ? null : cookie.getValue();
    }

}
