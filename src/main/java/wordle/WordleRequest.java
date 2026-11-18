package wordle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WordleRequest {
    // position index (starting at 0) -> letter
    private Map<Integer, String> green = new HashMap<>();
    private Map<Integer, String> yellow = new HashMap<>();
    private List<String> grey = new ArrayList<>();

    public Map<Integer, String> getGreen() {
        return green;
    }

    public void setGreen(Map<Integer, String> green) {
        this.green = green;
    }

    public Map<Integer, String> getYellow() {
        return yellow;
    }

    public void setYellow(Map<Integer, String> yellow) {
        this.yellow = yellow;
    }

    public List<String> getGrey() {
        return grey;
    }

    public void setGrey(List<String> grey) {
        this.grey = grey;
    }
}
