package ch.adibilis.jtg.model.types;

import java.util.List;

public record EnumType(String name, List<String> values, String packageSegment) implements Type {
}
