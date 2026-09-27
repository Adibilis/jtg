package ch.adibilis.jtg.parser.fixtures;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class SnakeCaseDto {
    private String firstName;
    private String lastName;
}
