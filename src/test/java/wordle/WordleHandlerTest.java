package wordle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.amazonaws.services.lambda.runtime.ClientContext;
import com.amazonaws.services.lambda.runtime.CognitoIdentity;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

class WordleHandlerTest {
    private final WordleHandler handler = new WordleHandler();

    private APIGatewayV2HTTPResponse call(String body) {
        APIGatewayV2HTTPEvent event = new APIGatewayV2HTTPEvent();
        event.setBody(body);
        return handler.handleRequest(event, new TestContext());
    }

    // the handler logs through the context, so tests need one that prints locally instead
    private static class TestContext implements Context {
        @Override
        public String getAwsRequestId() {
            return "test-request";
        }

        @Override
        public String getLogGroupName() {
            return "test-log-group";
        }

        @Override
        public String getLogStreamName() {
            return "test-log-stream";
        }

        @Override
        public String getFunctionName() {
            return "wordle-solver";
        }

        @Override
        public String getFunctionVersion() {
            return "test";
        }

        @Override
        public String getInvokedFunctionArn() {
            return "test-arn";
        }

        @Override
        public CognitoIdentity getIdentity() {
            return null;
        }

        @Override
        public ClientContext getClientContext() {
            return null;
        }

        @Override
        public int getRemainingTimeInMillis() {
            return 30000;
        }

        @Override
        public int getMemoryLimitInMB() {
            return 512;
        }

        @Override
        public LambdaLogger getLogger() {
            return new LambdaLogger() {
                @Override
                public void log(String message) {
                    System.out.println("[lambda log] " + message);
                }

                @Override
                public void log(byte[] message) {
                    System.out.println("[lambda log] " + new String(message));
                }
            };
        }
    }

    @Test
    void validRequestReturnsWordMatchingClues() {
        APIGatewayV2HTTPResponse response = call(
                "{\"green\":{\"0\":\"s\"},\"yellow\":{\"2\":\"r\"},\"grey\":[\"a\",\"l\",\"o\"]}");
        assertEquals(200, response.getStatusCode());

        String word = new Gson().fromJson(response.getBody(), WordleResponse.class).getWord();
        assertNotNull(word);
        assertEquals(5, word.length());
        assertEquals('s', word.charAt(0));
        assertTrue(word.contains("r"));
        assertTrue(word.charAt(2) != 'r');
        assertFalse(word.contains("a") || word.contains("l") || word.contains("o"));
    }

    @Test
    void impossibleCluesReturn404() {
        assertEquals(404, call("{\"green\":{\"0\":\"z\",\"1\":\"z\",\"2\":\"z\"}}").getStatusCode());
    }

    @Test
    void missingBodyReturns400() {
        assertEquals(400, call(null).getStatusCode());
    }

    @Test
    void invalidJsonReturns400() {
        assertEquals(400, call("{not json").getStatusCode());
    }

    @Test
    void noCluesReturnsAStartingWord() {
        APIGatewayV2HTTPResponse response = call("{}");
        assertEquals(200, response.getStatusCode());
        assertEquals(5, new Gson().fromJson(response.getBody(), WordleResponse.class).getWord().length());
    }

    @Test
    void invalidLetterReturns400() {
        assertEquals(400, call("{\"grey\":[\"ab\"]}").getStatusCode());
        assertEquals(400, call("{\"green\":{\"0\":\"1\"}}").getStatusCode());
        assertEquals(400, call("{\"yellow\":{\"0\":\"\"}}").getStatusCode());
    }

    @Test
    void positionOutOfRangeReturns400() {
        assertEquals(400, call("{\"green\":{\"5\":\"a\"}}").getStatusCode());
        assertEquals(400, call("{\"yellow\":{\"-1\":\"a\"}}").getStatusCode());
    }
}
