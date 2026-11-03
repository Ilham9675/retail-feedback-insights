package com.retailstore.feedback.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

/**
 * Service for interacting with Google's Gemini AI using REST API
 */
@Service
public class GeminiService {

    @Value("${gemini.api-key}")
    private String apiKey;

    // The lab used gemini-1.5-flash, which Google has retired; the model is configurable instead
    @Value("${gemini.model:gemini-flash-lite-latest}")
    private String model;

    // Timeouts so one slow or stuck Gemini call can't block the whole dashboard
    private final RestTemplate restTemplate = createRestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";

    // Free-tier keys are rate limited; on HTTP 429 wait and retry a few times
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 20_000;

    /**
     * Sends a prompt to Gemini and returns the response
     *
     * @param prompt The prompt to send to Gemini
     * @return The response from Gemini
     */
    public String generateContent(String prompt) {
        try {
            // Create the request body using Jackson
            ObjectNode requestBody = objectMapper.createObjectNode();
            ArrayNode contents = objectMapper.createArrayNode();
            ObjectNode content = objectMapper.createObjectNode();
            ArrayNode parts = objectMapper.createArrayNode();
            ObjectNode textPart = objectMapper.createObjectNode();

            textPart.put("text", prompt);
            parts.add(textPart);
            content.set("parts", parts);
            contents.add(content);
            requestBody.set("contents", contents);

            // Add generation config for better JSON responses
            ObjectNode generationConfig = objectMapper.createObjectNode();
            generationConfig.put("temperature", 0.7);
            generationConfig.put("maxOutputTokens", 1024);
            // Gemini 2.5 models "think" by default and those tokens count against maxOutputTokens;
            // a budget of 0 turns thinking off so the short JSON answer is not cut off
            if (model.startsWith("gemini-2.5")) {
                ObjectNode thinkingConfig = objectMapper.createObjectNode();
                thinkingConfig.put("thinkingBudget", 0);
                generationConfig.set("thinkingConfig", thinkingConfig);
            }
            requestBody.set("generationConfig", generationConfig);

            // Set headers; the API key is sent as a header instead of in the URL so it doesn't end up in logs
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-goog-api-key", apiKey);

            String url = String.format(GEMINI_API_URL, model);

            // Create the request
            HttpEntity<String> request = new HttpEntity<>(requestBody.toString(), headers);

            // Send the request
            ResponseEntity<String> response = sendWithRetry(url, request);

            // Parse the response
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                ObjectNode responseJson = (ObjectNode) objectMapper.readTree(response.getBody());
                ArrayNode candidates = (ArrayNode) responseJson.get("candidates");

                if (candidates != null && candidates.size() > 0) {
                    ObjectNode candidate = (ObjectNode) candidates.get(0);
                    ObjectNode candidateContent = (ObjectNode) candidate.get("content");
                    ArrayNode candidateParts = candidateContent == null ? null : (ArrayNode) candidateContent.get("parts");

                    if (candidateParts != null && candidateParts.size() > 0) {
                        return candidateParts.get(0).get("text").asText();
                    }
                }
            }

            return "No response from Gemini";

        } catch (Exception e) {
            System.err.println("Error calling Gemini API: " + e.getMessage());
            return "Error: " + e.getMessage();
        }
    }

    private static RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(30_000);
        return new RestTemplate(factory);
    }

    private ResponseEntity<String> sendWithRetry(String url, HttpEntity<String> request) throws InterruptedException {
        for (int attempt = 1; ; attempt++) {
            try {
                return restTemplate.exchange(url, HttpMethod.POST, request, String.class);
            } catch (HttpClientErrorException.TooManyRequests e) {
                if (attempt > MAX_RETRIES) {
                    throw e;
                }
                System.err.println("Gemini rate limit reached, retrying in " + RETRY_DELAY_MS / 1000 + "s (attempt " + attempt + ")");
                Thread.sleep(RETRY_DELAY_MS);
            }
        }
    }

    /**
     * Test method to verify API connection
     */
    public void testConnection() {
        String testPrompt = "Say 'Hello, World!' if you can hear me.";
        String response = generateContent(testPrompt);
        System.out.println("Gemini API Test Response: " + response);
    }
}
