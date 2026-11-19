package store.auth;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import store.auth.api.AccountOut;
import store.auth.api.LoginIn;
import store.auth.api.RegisterIn;
import store.auth.api.SolvedToken;

/**
 * Authentication flow: user and password checked by account-service, then a JWT pair (see JwtService).
 * No database here: tokens are self-contained and signed.
 */
@Service
public class AuthService {

    private final AccountClient accounts;
    private final JwtService jwt;

    public AuthService(AccountClient accounts, JwtService jwt) {
        this.accounts = accounts;
        this.jwt = jwt;
    }

    /** account-service validates and stores; its 400/409 pass through unchanged. */
    public AccountOut register(RegisterIn in) {
        return accounts.create(in);
    }

    /** 401 from account-service (unknown e-mail or wrong password) passes through unchanged. */
    public JwtService.Tokens login(LoginIn in) {
        return jwt.issue(accounts.login(in));
    }

    /**
     * New pair from a valid refresh token. The account is reloaded, so a deleted account can no longer
     * refresh (401) and the new access token carries its current name and e-mail.
     */
    public JwtService.Tokens refresh(String refreshToken) {
        String id = jwt.resolveRefresh(refreshToken);
        try {
            return jwt.issue(accounts.findById(id));
        } catch (ResponseStatusException e) {
            if (e.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "account no longer exists");
            }
            throw e;
        }
    }

    /** Called by the gateway on every secured request: signature and claims only, no call to account-service. */
    public SolvedToken solve(String token) {
        return new SolvedToken(jwt.resolveAccountId(token));
    }

    public AccountOut me(String idAccount) {
        return accounts.findById(idAccount);
    }

}
