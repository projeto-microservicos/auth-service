package store.auth;

import org.springframework.stereotype.Service;
import store.auth.api.AccountOut;
import store.auth.api.LoginIn;
import store.auth.api.RegisterIn;
import store.auth.api.SolvedToken;

/**
 * Authentication flow through account-service and signed JWTs.
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
        return accounts.create(in);
    }

    public String login(LoginIn in) {
        return jwt.generate(accounts.login(in));
    }

    public SolvedToken solve(String token) {
        return new SolvedToken(jwt.resolveAccountId(token));
    }

    public AccountOut me(String idAccount) {
        return accounts.findById(idAccount);
    }

}
