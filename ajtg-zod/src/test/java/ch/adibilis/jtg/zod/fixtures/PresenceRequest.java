package ch.adibilis.jtg.zod.fixtures;

import jakarta.validation.constraints.NotBlank;
import org.jspecify.annotations.Nullable;

public class PresenceRequest {
    @NotBlank
    private @Nullable String firstname;

    private @Nullable String nickname;
}
