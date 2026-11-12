package store.auth.api;

/** Response of {@code POST /auth/solve}: {"idAccount": "..."}, as the course gateway expects. */
public record SolvedToken(String idAccount) {}
