package ch.adibilis.jtg.parser.fixtures;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public class NullMarkedDto {
    private String required;
    private @Nullable String optional;
}
