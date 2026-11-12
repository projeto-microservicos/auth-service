package store.auth.api;

import jakarta.validation.constraints.NotBlank;

/** Body of {@code POST /auth/login}. */
public record LoginIn(
    @NotBlank String email,
    @NotBlank String password
) {}
