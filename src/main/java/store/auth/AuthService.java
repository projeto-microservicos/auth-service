package store.auth;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import store.auth.api.AccountOut;
import store.auth.api.LoginIn;
import store.auth.api.RegisterIn;
import store.auth.api.SolvedToken;

/**
 * Authentication flow. AccountClient (HTTP to account-service) and the cookie handling in
 * AuthResource are ready; the flow itself is yours. Disabled tests in AuthContractTest
 * describe the expected behavior.
 */
@Service
public class AuthService {

    private final AccountClient accounts;
    private final JwtService jwt;

    public AuthService(AccountClient accounts, JwtService jwt) {
        this.accounts = accounts;
        this.jwt = jwt;
    }

    public AccountOut register(RegisterIn in) {
        // TODO(auth): create the account through accounts.create(in).
        throw todo("register");
    }

    public String login(LoginIn in) {
        // TODO(auth): accounts.login(in) (401 on bad credentials) -> jwt.generate(account).
        throw todo("login");
    }

    public SolvedToken solve(String token) {
        // TODO(auth): new SolvedToken(jwt.resolveAccountId(token)). Called by the gateway on every
        //  secured request, so keep it cheap (no call to account-service here).
        throw todo("solve token");
    }

    public AccountOut me(String idAccount) {
        // TODO(auth): accounts.findById(idAccount).
        throw todo("me");
    }

    private static ResponseStatusException todo(String what) {
        return new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "TODO: " + what);
    }

}
