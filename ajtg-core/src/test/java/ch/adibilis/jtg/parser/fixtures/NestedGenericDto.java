package ch.adibilis.jtg.parser.fixtures;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class NestedGenericDto {
    private List<String> tags;
    private Map<String, Integer> scores;
    private Optional<String> nickname;
    private List<List<Integer>> matrix;
}
