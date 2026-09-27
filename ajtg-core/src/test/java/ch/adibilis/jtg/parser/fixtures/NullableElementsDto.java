package ch.adibilis.jtg.parser.fixtures;

import org.jspecify.annotations.Nullable;

import java.util.List;

public class NullableElementsDto {
    private List<@Nullable String> requiredListOfNullable;
    private @Nullable List<@Nullable String> optionalList;
}
