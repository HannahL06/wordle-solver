package wordle;

/**
 * Supplies the next guess for a game, given the clues so far.
 */
public interface Guesser {

    /**
     * @return the next guess, or null if there is none
     */
    String nextGuess(WordleRequest clues);
}
