package store.auth;

import jakarta.validation.Valid;
import java.net.URI;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import store.auth.api.AccountOut;
import store.auth.api.LoginIn;
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
        String token = service.login(in);
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, tokenCookie(token, properties.jwt().duration()).toString())
            .body(new TokenOut(token));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent()
            .header(HttpHeaders.SET_COOKIE, tokenCookie("", Duration.ZERO).toString())
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

    private ResponseCookie tokenCookie(String value, Duration maxAge) {
        var cookie = properties.cookie();
        return ResponseCookie.from(cookie.name(), value)
            .httpOnly(true)               // not readable from JavaScript (XSS)
            .secure(cookie.secure())      // HTTPS only in production
            .sameSite(cookie.sameSite())  // CSRF protection
            .path("/")
            .maxAge(maxAge)
            .build();
    }

}
