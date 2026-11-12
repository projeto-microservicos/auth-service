package store.auth.api;

/** Account as returned by account-service. Copied on purpose: services share JSON, not code. */
public record AccountOut(String id, String name, String email) {}
