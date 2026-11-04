package wordle;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Base64;
import java.nio.charset.StandardCharsets;

public class WordleHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    private final Gson gson = new Gson();
    // created once per Lambda container so the word list isn't reloaded on every request
    private final WordleSolver solver = new WordleSolver();

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent event, Context context) {
        try {
            String body = event.getBody();
            if (body == null || body.isBlank()) {
                return response(400, "Request body is required");
            }
            if (event.getIsBase64Encoded()) {
                body = new String(Base64.getDecoder().decode(body), StandardCharsets.UTF_8);
            }

            WordleRequest request = gson.fromJson(body, WordleRequest.class);
            if (request == null) {
                return response(400, "Request body is required");
            }

            String word = solver.solve(request);
            if (word == null) {
                return response(404, "No word matches the given letters");
            }
            return response(200, gson.toJson(new WordleResponse(word)));
        }
        catch (JsonSyntaxException e) {
            return response(400, "Invalid JSON: " + e.getMessage());
        }
        catch (Exception e) {
            // this prints the stack trace to the AWS log file
            e.printStackTrace();

            // this outputs the stack trace to the client
            return toResponseEvent(e);
        }
    }

    private APIGatewayV2HTTPResponse response(int statusCode, String body) {
        APIGatewayV2HTTPResponse apiResponse = new APIGatewayV2HTTPResponse();
        apiResponse.setStatusCode(statusCode);
        apiResponse.setBody(body);
        return apiResponse;
    }

    private APIGatewayV2HTTPResponse toResponseEvent(Exception e) {
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        e.printStackTrace(printWriter);
        return response(500, stringWriter.toString());
    }
}
