package ch.adibilis.jtg.parser.fixtures;

import com.fasterxml.jackson.annotation.JsonProperty;

public class JsonPropertyDto {
    @JsonProperty("display_name")
    private String displayName;
    private int value;
}
