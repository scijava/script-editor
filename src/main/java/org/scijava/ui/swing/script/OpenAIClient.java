package org.scijava.ui.swing.script;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class OpenAIClient {

    public static void main(String[] args) {
        String prompt = "Hello, GPT-4!";
        try {
            String response = prompt(prompt, "gpt-4o-2024-08-06", null, null);
            System.out.println("GPT-4 Response: " + response);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String prompt(String prompt, String model, String apikey, String apiurl) throws Exception {

        if (apiurl == null) {
            apiurl = "https://api.openai.com/v1/chat/completions";
        }
        if (apikey == null) {
            apikey = System.getenv("OPENAI_API_KEY");
        }

        URL url = new URL(apiurl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Authorization", "Bearer " + apikey);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);

        // Create JSON request body
        JSONObject requestBody = new JSONObject();
        requestBody.put("model", model);
        requestBody.put("messages", new JSONObject[]{
                new JSONObject().put("role", "user").put("content", prompt)
        });

        System.out.println(connection);
        System.out.println(requestBody);

        // Send request
        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = requestBody.toString().getBytes("utf-8");
            os.write(input, 0, input.length);
        }

        // Read response
        StringBuilder response = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), "utf-8"))) {
            String responseLine;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
        }

        // Parse JSON response
        JSONObject jsonResponse = new JSONObject(response.toString());
        return jsonResponse.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");
    }
}
