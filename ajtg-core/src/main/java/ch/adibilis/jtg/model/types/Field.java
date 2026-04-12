package ch.adibilis.jtg.model.types;

import ch.adibilis.jtg.validation.Validation;
import java.util.List;

public record Field(String name, Type type, boolean required, List<Validation> validations) {

    public Field(String name, Type type) {
        this(name, type, true, List.of());
    }

    public Field(String name, Type type, boolean required) {
        this(name, type, required, List.of());
    }
}
