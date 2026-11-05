# Wordle Solver

A Wordle solver that runs as an AWS Lambda function. You send it the clues from your Wordle guesses so far, as JSON, and it replies with a word to guess next. The project also includes `WordleGame`, a simulator that plays many games against the deployed function and reports how often it wins.

## How the Lambda works

The Lambda is reachable through a public Function URL. Send it a `POST` request with a JSON body.

### Request

```json
{
  "green":  {"0": "s", "4": "e"},
  "yellow": {"2": "r"},
  "grey":   ["a", "l", "o"]
}
```

| Field    | Type                      | Meaning                                                                 |
|----------|---------------------------|-------------------------------------------------------------------------|
| `green`  | map of position → letter  | The letter is in the word at that position (positions count from 0).    |
| `yellow` | map of position → letter  | The letter is in the word, but **not** at that position.                |
| `grey`   | list of letters           | The letter is not in the word.                                          |

Every field is optional. Letters must be single `a`–`z` characters, and positions must be 0 to 4.

### Response

```json
{"word": "shire"}
```

| Status | When                                                                              |
|--------|-----------------------------------------------------------------------------------|
| 200    | A word fits the clues. The body is `{"word": "..."}`.                              |
| 400    | Missing body, invalid JSON, a letter that is not a single `a`–`z`, or a position outside 0–4. |
| 404    | No word in the list fits the clues.                                                |
| 500    | Unexpected error. The body is the stack trace, which is also written to the logs.  |

### How it picks a word

1. **No clues** (`{}`): it returns a random word from the 200 best starting words. These are worked out once, when the Lambda container starts.
2. **With clues**: it keeps only the words that
   - have each green letter at its position,
   - contain each yellow letter, but not at the position where it was yellow,
   - contain none of the grey letters.
3. **A grey letter that is also green or yellow** only means "no more copies of this letter", so it is not used to rule the letter out.
4. **Choosing between the remaining words**: it scores each word by how common its distinct letters are among the remaining words, and returns the highest score. A letter counts once per word, so `eerie` gets credit for `e` only once. Ties go to the first word in the word list.

The scoring only looks at which letters are common. It does not consider letter position or how common the word is in English, so it sometimes guesses obscure words.

### Try it

With `curl`:

```bash
curl -X POST -H "Content-Type: application/json" \
     -d '{"green":{"0":"s"},"yellow":{"2":"r"},"grey":["a","l","o"]}' \
     <your-function-url>
```

In a browser, open the Function URL, press F12, and run this in the console:

```js
fetch(location.href, {
  method: 'POST',
  headers: {'Content-Type': 'application/json'},
  body: JSON.stringify({green: {0: 's'}, yellow: {2: 'r'}, grey: ['a', 'l', 'o']})
}).then(r => r.text()).then(console.log)
```

## Project structure

```
src/main/java/wordle/
  WordleRequest.java    The JSON request: green and yellow maps, and the grey list
  WordleResponse.java   The JSON response: one word
  WordleHandler.java    The Lambda entry point: parses the request, calls the solver,
                        builds the HTTP response, and logs each request
  WordleSolver.java     The solving logic: loads the word list, filters, scores, starting words
  WordleGame.java       The simulator: plays games against the deployed Lambda
src/main/resources/
  wordleWords.txt       The word list (five-letter words, one per line)
src/test/java/wordle/   JUnit tests for the solver, handler and game
LICENSE-words.txt       Licence for the word list
```

How the pieces fit together:

- **`WordleHandler`** is the only class AWS calls. It reads the request body, turns it into a `WordleRequest` with Gson, and asks `WordleSolver` for a word. It replies with a `WordleResponse` as JSON, or an error status. Each request is written to the Lambda log (CloudWatch).
- **`WordleSolver`** has no AWS code, so it can be tested on its own. It loads the word list once when the container starts, and builds the list of starting words at the same time.
- **`WordleGame`** is a client. It runs on your own computer, not on AWS, and calls the Lambda over HTTP.

## Build and deploy

Requires Java 21.

Build the jar with all dependencies (a "fat jar"):

```bash
./gradlew shadowJar
```

This creates `build/libs/wordle-solver-1.0-SNAPSHOT-all.jar`. Always use the file ending in `-all.jar`.

To create the Lambda in the AWS console:

1. Create a function. Runtime: **Java 21**.
2. On the **Code** tab, choose **Upload from → .zip or .jar file** and select the `-all.jar`.
3. Under **Runtime settings**, set the handler to `wordle.WordleHandler`.
4. On the **Configuration** tab, create a **Function URL** with Auth type **NONE**.

To deploy a new version, rebuild the jar and upload it again. Uploading replaces the code but does not create a numbered Lambda version.

### Viewing the Lambda's logs

In the Lambda console, open the **Monitor** tab and choose **View CloudWatch logs**. Each request writes one line with the clues received and one line with the result (`200`, `404` or `400`).

## Running the game

`WordleGame` plays Wordle against your deployed Lambda. For each round it picks a random secret word from the word list, then asks the Lambda for up to 6 guesses. After each wrong guess it works out the green, yellow and grey clues and sends them back. A round is a **win** if the Lambda guesses the word within 6 guesses. It prints every round, and at the end the win percentage.

You need your Function URL and a built jar.

1. Build the jar:

   ```bash
   ./gradlew shadowJar
   ```

2. Run the game. The arguments are the Function URL, then the number of rounds (it defaults to 1):

   ```bash
   java -cp build/libs/wordle-solver-1.0-SNAPSHOT-all.jar wordle.WordleGame <your-function-url> 10
   ```

   Instead of passing the URL, you can set the `WORDLE_FUNCTION_URL` environment variable. The URL is deliberately not stored in the repository.

### Running from IntelliJ

1. Open `WordleGame.java` and click the green arrow next to `main`.
2. Choose **Modify Run Configuration** and enter `<your-function-url> 10` in **Program arguments**.
3. Run it.

### Reading the output

```
Round 1 (secret word: rubes)
  Request 1: green={} yellow={} grey=[]
  Response 1: swear
  Request 2: green={} yellow={0=s, 2=e, 4=r} grey=[w, a]
  Response 2: risen
  ...
  WIN on guess 4
...
Win rate over 10 round(s): 90.0%
```

- `Request n` shows the clues sent to the Lambda for guess n.
- `Response n` shows the word the Lambda returned.
- Each round ends with `WIN on guess n` or `LOSS`.

A 10-round game makes between 10 and 60 requests, and a 100-round game takes about half a minute. Every request goes to your live Lambda, so use a modest number of rounds.

## Tests

```bash
./gradlew test
```

The tests cover the solver, the handler, and the game's clue logic.

## Known limitations

- A map holds one yellow letter per position, so if two guesses give different yellow letters at the same position, the older clue is overwritten.
- Guesses are chosen by letter frequency only, so the solver does not always pick the guess that narrows the options most.
- The function URL has no authentication, so anyone who has the link can call it.

## Word list

The word list in `src/main/resources/wordleWords.txt` comes from the `cheaderthecoder/5-letter-words` repository on GitHub and is used under the MIT licence. The licence text is in `LICENSE-words.txt`.
