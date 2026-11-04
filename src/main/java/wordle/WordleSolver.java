package wordle;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class WordleSolver {
    private static final String WORD_FILE = "/wordleWords.txt";

    private final List<String> words;

    public WordleSolver() {
        this(loadWords());
    }

    public WordleSolver(List<String> words) {
        this.words = words;
    }

    /**
     * Returns the most promising word that fits the clues, or null if none fit.
     */
    public String solve(WordleRequest request) {
        Map<Integer, String> green = request.getGreen() == null ? Map.of() : request.getGreen();
        Map<Integer, String> yellow = request.getYellow() == null ? Map.of() : request.getYellow();
        List<String> grey = request.getGrey() == null ? List.of() : request.getGrey();

        // a grey letter that is also green or yellow only means "no more copies",
        // so it can't be used to rule out the letter entirely
        Set<String> greyLetters = new HashSet<>();
        for (String letter : grey) {
            greyLetters.add(letter.toLowerCase(Locale.ROOT));
        }
        for (String letter : green.values()) {
            greyLetters.remove(letter.toLowerCase(Locale.ROOT));
        }
        for (String letter : yellow.values()) {
            greyLetters.remove(letter.toLowerCase(Locale.ROOT));
        }

        List<String> candidates = new ArrayList<>();
        for (String word : words) {
            if (matches(word, green, yellow, greyLetters)) {
                candidates.add(word);
            }
        }
        return pickBest(candidates);
    }

    private boolean matches(String word, Map<Integer, String> green, Map<Integer, String> yellow,
                            Set<String> greyLetters) {
        for (Map.Entry<Integer, String> entry : green.entrySet()) {
            int index = entry.getKey();
            String letter = entry.getValue().toLowerCase(Locale.ROOT);
            if (index < 0 || index >= word.length() || word.charAt(index) != letter.charAt(0)) {
                return false;
            }
        }
        for (Map.Entry<Integer, String> entry : yellow.entrySet()) {
            int index = entry.getKey();
            String letter = entry.getValue().toLowerCase(Locale.ROOT);
            if (word.indexOf(letter.charAt(0)) < 0
                    || (index >= 0 && index < word.length() && word.charAt(index) == letter.charAt(0))) {
                return false;
            }
        }
        for (String letter : greyLetters) {
            if (word.indexOf(letter.charAt(0)) >= 0) {
                return false;
            }
        }
        return true;
    }

    // scores each word by how many candidates share each of its distinct letters
    private String pickBest(List<String> candidates) {
        if (candidates.isEmpty()) {
            return null;
        }
        int[] letterCounts = new int[26];
        for (String word : candidates) {
            for (char c : distinctLetters(word)) {
                letterCounts[c - 'a']++;
            }
        }
        String best = null;
        int bestScore = -1;
        for (String word : candidates) {
            int score = 0;
            for (char c : distinctLetters(word)) {
                score += letterCounts[c - 'a'];
            }
            if (score > bestScore) {
                bestScore = score;
                best = word;
            }
        }
        return best;
    }

    private char[] distinctLetters(String word) {
        return word.chars()
                .filter(c -> c >= 'a' && c <= 'z')
                .distinct()
                .mapToObj(c -> String.valueOf((char) c))
                .collect(java.util.stream.Collectors.joining())
                .toCharArray();
    }

    private static List<String> loadWords() {
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
