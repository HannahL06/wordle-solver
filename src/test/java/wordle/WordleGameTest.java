package wordle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class WordleGameTest {
    @Test
    void solverThatGuessesTheSecretWinsEveryRound() {
        List<String> words = List.of("crane");
        assertEquals(100.0, new WordleGame(words, clues -> "crane").play(10));
    }

    @Test
    void solverThatNeverGuessesTheSecretLosesEveryRound() {
        List<String> words = List.of("crane");
        assertEquals(0.0, new WordleGame(words, clues -> "zzzzz").play(10));
    }

    @Test
    void noGuessCountsAsALoss() {
        assertEquals(0.0, new WordleGame(List.of("crane"), clues -> null).play(5));
    }

    @Test
    void winningOnTheSixthGuessCountsButTheSeventhDoesNot() {
        assertEquals(100.0, new WordleGame(List.of("crane"), winsOnGuess(6)).play(3));
        assertEquals(0.0, new WordleGame(List.of("crane"), winsOnGuess(7)).play(3));
    }

    // guesses "zzzzz" until the given guess number of the round, then guesses "crane"
    private Guesser winsOnGuess(int winningGuess) {
        int[] guessNumber = {0};
        return clues -> {
            boolean newRound = clues.getGreen().isEmpty() && clues.getYellow().isEmpty()
                    && clues.getGrey().isEmpty();
            if (newRound) {
                guessNumber[0] = 0;
            }
            return ++guessNumber[0] == winningGuess ? "crane" : "zzzzz";
        };
    }

    @Test
    void feedbackSentToTheSolverFollowsWordleRules() {
        List<WordleRequest> seen = new ArrayList<>();
        String[] guesses = {"slate", "eerie", "crane"};
        int[] next = {0};
        WordleGame game = new WordleGame(List.of("crane"), clues -> {
            // copy the clues, because the game keeps adding to the same object
            WordleRequest copy = new WordleRequest();
            copy.getGreen().putAll(clues.getGreen());
            copy.getYellow().putAll(clues.getYellow());
            copy.getGrey().addAll(clues.getGrey());
            seen.add(copy);
            return guesses[next[0]++];
        });

        assertEquals(100.0, game.play(1));

        // first guess: no clues yet
        assertTrue(seen.get(0).getGreen().isEmpty());
        assertTrue(seen.get(0).getYellow().isEmpty());
        assertTrue(seen.get(0).getGrey().isEmpty());

        // slate vs crane: a and e are in the right place, s, l and t are not in the word
        assertEquals(Map.of(2, "a", 4, "e"), seen.get(1).getGreen());
        assertTrue(seen.get(1).getYellow().isEmpty());
        assertEquals(List.of("s", "l", "t"), seen.get(1).getGrey());

        // eerie vs crane: the final e is green, so the other two es get no yellow,
        // r is in the word but not at index 2, i is not in the word
        assertEquals(Map.of(2, "r"), seen.get(2).getYellow());
        assertEquals(Map.of(2, "a", 4, "e"), seen.get(2).getGreen());
        assertTrue(seen.get(2).getGrey().containsAll(List.of("e", "i")));
    }

    @Test
    void solverWinsMostRoundsOnARealisticList() {
        List<String> words = List.of(
                "crane", "slate", "trace", "crate", "plumb", "roate", "grate", "brace", "stare", "share");
        WordleSolver solver = new WordleSolver(words, new Random(1));
        double percent = new WordleGame(words, solver::solve, new Random(2)).play(50);
        assertTrue(percent >= 90.0, "win rate was " + percent);
    }

    // runs a tiny local web server that answers like the Lambda, to check the Retrofit client
    private HttpServer startServer(int status, String responseBody, List<String> requestBodies) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            requestBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return server;
    }

    private String urlOf(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Test
    void retrofitClientPostsTheCluesAndReadsTheWord() throws IOException {
        List<String> requests = new ArrayList<>();
        HttpServer server = startServer(200, """
                {"word": "crane"}""", requests);
        try {
            WordleGame game = new WordleGame(List.of("crane"), WordleGame.overRetrofit(urlOf(server)));
            assertEquals(100.0, game.play(1));
            assertEquals("""
                    {"green":{},"yellow":{},"grey":[]}""", requests.get(0));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void retrofitClientTreatsA404AsNoWord() throws IOException {
        List<String> requests = new ArrayList<>();
        HttpServer server = startServer(404, "No word matches the given letters", requests);
        try {
            WordleGame game = new WordleGame(List.of("crane"), WordleGame.overRetrofit(urlOf(server)));
            assertEquals(0.0, game.play(1));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void retrofitClientReportsOtherErrors() throws IOException {
        List<String> requests = new ArrayList<>();
        HttpServer server = startServer(500, "boom", requests);
        try {
            WordleGame game = new WordleGame(List.of("crane"), WordleGame.overRetrofit(urlOf(server)));
            IllegalStateException error = assertThrows(IllegalStateException.class, () -> game.play(1));
            assertTrue(error.getMessage().contains("500"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void roundsMustBePositive() {
        WordleGame game = new WordleGame(List.of("crane"), clues -> "crane");
        assertThrows(IllegalArgumentException.class, () -> game.play(0));
    }
}
