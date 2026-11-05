package wordle;

import com.google.gson.Gson;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;

/**
 * Plays Wordle against the solver to measure how often it wins.
 */
public class WordleGame {
    private static final int MAX_GUESSES = 6;

    private final List<String> words;
    private final Function<WordleRequest, String> guesser;
    private final Random random;

    /**
     * Plays against the deployed Lambda at the given Function URL.
     */
    public WordleGame(String functionUrl) {
        this(WordleSolver.loadWords(), overHttp(functionUrl));
    }

    /**
     * @param guesser takes the clues so far and returns the next guess, or null if there is none
     */
    public WordleGame(List<String> words, Function<WordleRequest, String> guesser) {
        this(words, guesser, new Random());
    }

    public WordleGame(List<String> words, Function<WordleRequest, String> guesser, Random random) {
        this.words = words;
        this.guesser = guesser;
        this.random = random;
    }

    /**
     * Plays the given number of rounds, each with a random secret word from the word list,
     * and returns the percentage (0 to 100) that the solver won within six guesses.
     */
    public double play(int rounds) {
        if (rounds < 1) {
            throw new IllegalArgumentException("rounds must be at least 1: " + rounds);
        }
        if (words.isEmpty()) {
            throw new IllegalStateException("The word list is empty");
        }
        int wins = 0;
        for (int round = 0; round < rounds; round++) {
            String secret = words.get(random.nextInt(words.size()));
            if (playRound(round + 1, secret)) {
                wins++;
            }
        }
        return 100.0 * wins / rounds;
    }

    /**
     * Plays against the deployed Lambda and prints each round.
     * Usage: WordleGame [function-url] [rounds]. The URL can also come from the
     * WORDLE_FUNCTION_URL environment variable; rounds defaults to 1.
     */
    public static void main(String[] args) {
        String url = args.length > 0 ? args[0] : System.getenv("WORDLE_FUNCTION_URL");
        if (url == null || url.isBlank()) {
            System.err.println("Usage: WordleGame <function-url> [rounds]"
                    + " (or set WORDLE_FUNCTION_URL)");
            return;
        }
        int rounds = args.length > 1 ? Integer.parseInt(args[1]) : 1;
        double winRate = new WordleGame(url).play(rounds);
        System.out.printf("Win rate over %d round(s): %.1f%%%n", rounds, winRate);
    }

    private boolean playRound(int round, String secret) {
        System.out.println("Round " + round + " (secret word: " + secret + ")");
        WordleRequest clues = new WordleRequest();
        for (int guessNumber = 1; guessNumber <= MAX_GUESSES; guessNumber++) {
            System.out.println("  Request " + guessNumber + ": green=" + clues.getGreen()
                    + " yellow=" + clues.getYellow() + " grey=" + clues.getGrey());
            String guess = guesser.apply(clues);
            System.out.println("  Response " + guessNumber + ": " + (guess == null ? "no word" : guess));
            if (guess == null || guess.length() != secret.length()) {
                System.out.println("  LOSS");
                return false;
            }
            if (guess.equals(secret)) {
                System.out.println("  WIN on guess " + guessNumber);
                return true;
            }
            addFeedback(clues, guess, secret);
        }
        System.out.println("  LOSS");
        return false;
    }

    // adds what Wordle would show for this guess to the clues: green for the right spot,
    // yellow for a letter elsewhere in the word, grey for a letter with no copy left
    private void addFeedback(WordleRequest clues, String guess, String secret) {
        int[] unmatched = new int[26];
        boolean[] isGreen = new boolean[secret.length()];
        for (int i = 0; i < secret.length(); i++) {
            if (guess.charAt(i) == secret.charAt(i)) {
                isGreen[i] = true;
                clues.getGreen().put(i, String.valueOf(guess.charAt(i)));
            } else {
                unmatched[secret.charAt(i) - 'a']++;
            }
        }
        for (int i = 0; i < guess.length(); i++) {
            if (isGreen[i]) {
                continue;
            }
            char letter = guess.charAt(i);
            if (unmatched[letter - 'a'] > 0) {
                unmatched[letter - 'a']--;
                clues.getYellow().put(i, String.valueOf(letter));
            } else if (!clues.getGrey().contains(String.valueOf(letter))) {
                clues.getGrey().add(String.valueOf(letter));
            }
        }
    }

    private static Function<WordleRequest, String> overHttp(String functionUrl) {
        Gson gson = new Gson();
        HttpClient client = HttpClient.newHttpClient();
        return clues -> {
            HttpRequest request = HttpRequest.newBuilder(URI.create(functionUrl))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(clues)))
                    .build();
            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 404) {
                    return null;
                }
                if (response.statusCode() != 200) {
                    throw new IllegalStateException(
                            "Function returned " + response.statusCode() + ": " + response.body());
                }
                return gson.fromJson(response.body(), WordleResponse.class).getWord();
            } catch (IOException e) {
                throw new IllegalStateException("Request to the function failed", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while calling the function", e);
            }
        };
    }
}
