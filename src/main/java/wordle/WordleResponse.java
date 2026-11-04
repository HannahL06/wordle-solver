package wordle;

public class WordleResponse {
    private String word;

    public WordleResponse() {
    }

    public WordleResponse(String word) {
        this.word = word;
    }

    public String getWord() {
        return word;
    }

    public void setWord(String word) {
        this.word = word;
    }
}
