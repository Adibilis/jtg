package ch.adibilis.jtg.parser.fixtures;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class IgnoredFieldsDto {
    private String visible;
    @JsonIgnore
    private String hidden;
    private static String CONSTANT = "ignored";
}
