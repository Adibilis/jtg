package ch.adibilis.jtg.parser.fixtures;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.groups.Default;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class GroupedPresenceDto {
    public interface OnCreate {}
    public interface Details {}
    public interface Extended extends Default {}

    @NotNull(groups = OnCreate.class) @Size(min = 1, groups = OnCreate.class)
    private @Nullable List<Long> createOnly;

    @NotBlank(groups = {Default.class, Details.class})
    private @Nullable String withDefault;

    @NotNull(groups = Extended.class)
    private @Nullable String inheritsDefault;

    @NotNull
    private @Nullable String plain;
}
