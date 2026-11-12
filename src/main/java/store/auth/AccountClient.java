package store.auth;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import store.auth.api.AccountOut;
import store.auth.api.LoginIn;
import store.auth.api.RegisterIn;

/**
 * HTTP client for account-service (same timeouts as order-service's StoreClients).
 *
 * <p>Error mapping: a 4xx from account-service keeps its status (409 duplicate e-mail,
 * 401 bad credentials, 404 unknown id); a 5xx becomes 502; no connection becomes 503.
 */
@Component
public class AccountClient {

    private final RestClient accounts;

    public AccountClient(AuthProperties properties) {
        var factory = new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.accounts = RestClient.builder().requestFactory(factory).baseUrl(properties.accountUrl()).build();
    }

    public AccountOut create(RegisterIn in) {
        return call(() -> accounts.post().uri("/accounts").body(in).retrieve().body(AccountOut.class));
    }

    public AccountOut login(LoginIn in) {
        return call(() -> accounts.post().uri("/accounts/login").body(in).retrieve().body(AccountOut.class));
    }

    public AccountOut findById(String id) {
        return call(() -> accounts.get().uri("/accounts/{id}", id).retrieve().body(AccountOut.class));
    }

    private static <T> T call(Supplier<T> request) {
        try {
            return request.get();
        } catch (HttpClientErrorException e) {
            throw new ResponseStatusException(e.getStatusCode(), "account-service: " + e.getStatusText());
        } catch (HttpServerErrorException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "account-service failed");
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "account-service unavailable");
        }
    }

}
