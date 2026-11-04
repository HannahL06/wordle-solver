package wordle;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class WordleSolver {
    private static final String WORD_FILE = "/wordleWords.txt";
    private static final int WORD_LENGTH = 5;
    private static final int START_WORDS_SIZE = 200;

    private final List<String> words;
    private final Random random;
    // worked out once when the solver is created, then reused for every first round
    private final List<String> startingWords;

    public WordleSolver() {
        this(loadWords());
    }

    public WordleSolver(List<String> words) {
        this(words, new Random());
    }

    public WordleSolver(List<String> words, Random random) {
        this.words = words;
        this.random = random;
        this.startingWords = genrateStartingWords();
    }

    /**
     * Returns a word that fits the clues, or null if none fit. With no clues at all
     * it returns a random word from the top-scoring starting words.
     *
     * @throws IllegalArgumentException if a letter is not a single a-z letter
     *                                  or a position is outside the word
     */
    public String solve(WordleRequest request) {
        Map<Integer, Character> green = toLetters(request.getGreen());
        Map<Integer, Character> yellow = toLetters(request.getYellow());
        Set<Character> grey = new HashSet<>();
        if (request.getGrey() != null) {
            for (String letter : request.getGrey()) {
                grey.add(toLetter(letter));
            }
        }

        if (green.isEmpty() && yellow.isEmpty() && grey.isEmpty()) {
            return startingWord();
        }

        // a grey letter that is also green or yellow only means "no more copies",
        // so it can't be used to rule out the letter entirely
        grey.removeAll(green.values());
        grey.removeAll(yellow.values());

        List<String> candidates = new ArrayList<>();
        for (String word : words) {
            if (matches(word, green, yellow, grey)) {
                candidates.add(word);
            }
        }
        return pickBest(candidates);
    }

    /**
     * Picks at random from the best starting words, so the first guess varies.
     * Returns null if there are no words.
     */
    public String startingWord() {
        return startingWords.isEmpty() ? null : startingWords.get(random.nextInt(startingWords.size()));
    }

    /**
     * Ranks the whole word list and returns the 200 highest-scoring words (fewer if the list
     * is smaller), best first. The constructor calls this once and stores the result.
     */
    public List<String> genrateStartingWords() {
        List<String> ranked = rankByLetterFrequency(words);
        return List.copyOf(ranked.subList(0, Math.min(START_WORDS_SIZE, ranked.size())));
    }

    private boolean matches(String word, Map<Integer, Character> green, Map<Integer, Character> yellow,
                            Set<Character> grey) {
        for (Map.Entry<Integer, Character> entry : green.entrySet()) {
            if (word.charAt(entry.getKey()) != entry.getValue()) {
                return false;
            }
        }
        for (Map.Entry<Integer, Character> entry : yellow.entrySet()) {
            char letter = entry.getValue();
            if (word.indexOf(letter) < 0 || word.charAt(entry.getKey()) == letter) {
                return false;
            }
        }
        for (char letter : grey) {
            if (word.indexOf(letter) >= 0) {
                return false;
            }
        }
        return true;
    }

    // the highest-scoring word, or null if there are none; the first one wins a tie
    private String pickBest(List<String> candidates) {
        int[] letterCounts = countLetters(candidates);
        String best = null;
        int bestScore = -1;
        for (String word : candidates) {
            int score = score(word, letterCounts);
            if (score > bestScore) {
                bestScore = score;
                best = word;
            }
        }
        return best;
    }

    // best first: scores each word by how many words share each of its distinct letters
    private List<String> rankByLetterFrequency(List<String> candidates) {
        int[] letterCounts = countLetters(candidates);
        Map<String, Integer> scores = new HashMap<>();
        for (String word : candidates) {
            scores.put(word, score(word, letterCounts));
        }
        List<String> ranked = new ArrayList<>(candidates);
        ranked.sort(Comparator.comparingInt((String word) -> scores.get(word)).reversed());
        return ranked;
    }

    // how many of the words contain each letter
    private int[] countLetters(List<String> candidates) {
        int[] letterCounts = new int[26];
        for (String word : candidates) {
            int mask = letterMask(word);
            for (int i = 0; i < 26; i++) {
                if ((mask & (1 << i)) != 0) {
                    letterCounts[i]++;
                }
            }
        }
        return letterCounts;
    }

    private int score(String word, int[] letterCounts) {
        int mask = letterMask(word);
        int score = 0;
        for (int i = 0; i < 26; i++) {
            if ((mask & (1 << i)) != 0) {
                score += letterCounts[i];
            }
        }
        return score;
    }

    private int letterMask(String word) {
        int mask = 0;
        for (char c : word.toCharArray()) {
            if (c >= 'a' && c <= 'z') {
                mask |= 1 << (c - 'a');
            }
        }
        return mask;
    }

    private Map<Integer, Character> toLetters(Map<Integer, String> clues) {
        Map<Integer, Character> letters = new HashMap<>();
        if (clues == null) {
            return letters;
        }
        for (Map.Entry<Integer, String> entry : clues.entrySet()) {
            Integer position = entry.getKey();
            if (position == null || position < 0 || position >= WORD_LENGTH) {
                throw new IllegalArgumentException(
                        "Position must be between 0 and " + (WORD_LENGTH - 1) + ": " + position);
            }
            letters.put(position, toLetter(entry.getValue()));
        }
        return letters;
    }

    private char toLetter(String letter) {
        if (letter == null || letter.length() != 1) {
            throw new IllegalArgumentException("Each letter must be a single character: " + letter);
        }
        char c = letter.toLowerCase(Locale.ROOT).charAt(0);
        if (c < 'a' || c > 'z') {
            throw new IllegalArgumentException("Each letter must be a-z: " + letter);
        }
        return c;
    }

    public static List<String> loadWords() {
        InputStream stream = WordleSolver.class.getResourceAsStream(WORD_FILE);
        if (stream == null) {
            throw new IllegalStateException("Word list not found: " + WORD_FILE);
        }
        List<String> loaded = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String word = line.trim().toLowerCase(Locale.ROOT);
                if (!word.isEmpty() && !word.startsWith("#")) {
                    loaded.add(word);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return loaded;
    }
}
