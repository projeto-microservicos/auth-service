package store.auth.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /auth/register}. Forwarded to account-service as its AccountIn. */
public record RegisterIn(
    @NotBlank @Size(max = 256) String name,
    @NotBlank @Email @Size(max = 256) String email,
    @NotBlank @Size(max = 72) String password
) {}
