package store.auth.api;

import jakarta.validation.constraints.NotBlank;

/** Body of {@code POST /auth/solve}, sent by the gateway: {"token": "..."}. */
public record TokenIn(@NotBlank String token) {}
