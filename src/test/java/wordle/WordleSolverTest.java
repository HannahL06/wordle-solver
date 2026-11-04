package wordle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;
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
}
