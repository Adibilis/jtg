package ch.adibilis.jtg.parser.fixtures;

import jakarta.validation.constraints.*;

public class ValidatedDto {
    @Min(value = 0, message = "must be positive")
    @Max(100)
    private int score;

    @Size(min = 1, max = 255)
    @NotBlank
    private String name;

    @Email(message = "invalid email")
    private String email;

    @Pattern(regexp = "^[A-Z]{2}\\d{4}$")
    private String code;
}
