package ch.adibilis.jtg.parser.fixtures;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class SnakeCaseDto {
    private String firstName;
    private String lastName;
}
