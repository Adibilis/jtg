package ch.adibilis.jtg.validation;

public sealed interface Validation {

    String message();

    record Min(long value, String message) implements Validation {}
    record Max(long value, String message) implements Validation {}
    record Size(int min, int max, String message) implements Validation {}
    record NotBlank(String message) implements Validation {}
    record Pattern(String regexp, String message) implements Validation {}
    record Email(String message) implements Validation {}
}
