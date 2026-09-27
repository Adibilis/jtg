package ch.adibilis.jtg.parser.fixtures;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class PresenceBeatsNullableDto {
    @NotNull
    private @Nullable String jspecifyNotNull;

    @NotBlank
    @org.springframework.lang.Nullable
    private String springNotBlank;

    @NotEmpty
    private @Nullable List<String> jspecifyNotEmpty;

    private @Nullable String plainOptional;
}
