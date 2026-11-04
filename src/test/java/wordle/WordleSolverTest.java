package wordle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WordleSolverTest {
    private final WordleSolver solver = new WordleSolver(
            List.of("crane", "slate", "trace", "crate", "eerie", "plumb", "roate"));

    @Test
    void greenLetterMustBeAtPosition() {
        WordleRequest request = new WordleRequest();
        request.setGreen(Map.of(0, "s"));
        assertEquals("slate", solver.solve(request));
    }

    @Test
    void yellowLetterMustBePresentButNotAtPosition() {
        WordleRequest request = new WordleRequest();
        // crane, trace and crate all have r at index 1, so only roate and eerie remain
        request.setYellow(Map.of(1, "r"));
        assertEquals(true, List.of("roate", "eerie").contains(solver.solve(request)));
    }

    @Test
    void greyLettersAreExcluded() {
        WordleRequest request = new WordleRequest();
        request.setGrey(List.of("a", "e", "r", "c", "s"));
        assertEquals("plumb", solver.solve(request));
    }

    @Test
    void greyLetterAlsoGreenIsNotExcluded() {
        WordleRequest request = new WordleRequest();
        request.setGreen(Map.of(0, "e"));
        request.setGrey(List.of("e"));
        assertEquals("eerie", solver.solve(request));
    }

    @Test
    void noMatchReturnsNull() {
        WordleRequest request = new WordleRequest();
        request.setGreen(Map.of(0, "z"));
        assertNull(solver.solve(request));
    }

    @Test
    void emptyRequestStillReturnsAWord() {
        assertEquals(true, solver.solve(new WordleRequest()) != null);
    }

    @Test
    void genrateStartingWordsExcludesLowScoringWords() {
        // 225 words that all share a, b and c score far higher than the one outlier
        String pairLetters = "defghijklmnopqr";
        List<String> words = new ArrayList<>();
        for (char first : pairLetters.toCharArray()) {
            for (char second : pairLetters.toCharArray()) {
                words.add("abc" + first + second);
            }
        }
        words.add("xyzxy");

        assertEquals(200, new WordleSolver(words).genrateStartingWords().size());

        Set<String> seen = new HashSet<>();
        for (int seed = 0; seed < 300; seed++) {
            String word = new WordleSolver(words, new Random(seed)).startingWord();
            assertNotEquals("xyzxy", word);
            seen.add(word);
        }
        // picks vary rather than always returning the same word
        assertEquals(true, seen.size() > 1);
    }

    @Test
    void startingWordIsNullWithNoWords() {
        assertNull(new WordleSolver(List.of()).startingWord());
    }

    @Test
    void invalidLettersAreRejected() {
        WordleRequest blank = new WordleRequest();
        blank.setYellow(Map.of(0, ""));
        assertThrows(IllegalArgumentException.class, () -> solver.solve(blank));

        WordleRequest digit = new WordleRequest();
        digit.setGrey(List.of("1"));
        assertThrows(IllegalArgumentException.class, () -> solver.solve(digit));

        WordleRequest tooLong = new WordleRequest();
        tooLong.setGreen(Map.of(0, "ab"));
        assertThrows(IllegalArgumentException.class, () -> solver.solve(tooLong));
    }

    @Test
    void positionOutOfRangeIsRejected() {
        WordleRequest high = new WordleRequest();
        high.setGreen(Map.of(5, "a"));
        assertThrows(IllegalArgumentException.class, () -> solver.solve(high));

        WordleRequest negative = new WordleRequest();
        negative.setYellow(Map.of(-1, "a"));
        assertThrows(IllegalArgumentException.class, () -> solver.solve(negative));
    }

    @Test
    void contradictoryGreenAndYellowReturnsNull() {
        WordleRequest request = new WordleRequest();
        request.setGreen(Map.of(0, "s"));
        request.setYellow(Map.of(0, "s"));
        assertNull(solver.solve(request));
    }
}
