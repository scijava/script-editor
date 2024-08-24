package org.scijava.ui.swing.script;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class ClaudeApiClient {

    public static String prompt(String prompt, String model, String apikey, String apiurl) throws IOException {

        if (apiurl == null) {
            apiurl = "https://api.anthropic.com/v1/messages";
        }
        if (apikey == null) {
            apikey = System.getenv("ANTHROPIC_API_KEY");
        }

        System.out.println("API KEY:" + apikey);

        URL url = new URL(apiurl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("x-api-key", apikey);
        connection.setRequestProperty("anthropic-version", "2023-06-01");
        connection.setRequestProperty("content-type", "application/json");
        connection.setDoOutput(true);

        JSONObject requestBody = new JSONObject();
        requestBody.put("model", model);
        requestBody.put("max_tokens", 8192);
        requestBody.put("messages", new JSONObject[]{
                new JSONObject().put("role", "user").put("content", prompt)
        });


        // Send request
        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = requestBody.toString().getBytes("utf-8");
            os.write(input, 0, input.length);
        }

        int responseCode = connection.getResponseCode();

        StringBuilder response = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(
                        responseCode >= 400 ? connection.getErrorStream() : connection.getInputStream(),
                        StandardCharsets.UTF_8
                )
        )) {
            String responseLine;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
        }

        //System.out.println("Response Code: " + responseCode);
        //System.out.println("Full response: " + response.toString());

        if (responseCode >= 400) {
            return "Error: " + response.toString();
        }

        try {
            JSONObject jsonObject = new JSONObject(response.toString());
            String content = jsonObject.getJSONArray("content").getJSONObject(0).getString("text");
            return content;
        } catch (Exception e) {
            System.out.println("Error parsing JSON: " + e.getMessage());
            return null;
        }
    }

    public static void main(String[] args) throws IOException {
        String input = "Hello, Claude! How are you today?";
        String response = prompt(input, "claude-3-5-sonnet-20240620", null, null);
        System.out.println("Claude's response: " + response);
    }
}