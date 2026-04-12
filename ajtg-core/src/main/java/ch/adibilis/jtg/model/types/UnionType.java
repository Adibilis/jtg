package ch.adibilis.jtg.model.types;

import java.util.List;

public record UnionType(String name, List<ObjectType> variants, String packageSegment,
                        String discriminatorProperty) implements Type {
}
