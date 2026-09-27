package ch.adibilis.jtg.parser.fixtures;

import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;

public record NullableRecord(
        String required,
        @Nullable String jspecifyOptional,
        @org.springframework.lang.Nullable String springOptional,
        @NotNull @Nullable String constrained) {
}
