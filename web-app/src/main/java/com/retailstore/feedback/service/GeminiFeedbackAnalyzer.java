package com.retailstore.feedback.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.retailstore.feedback.model.AiReview;
import com.retailstore.feedback.model.FeedbackEntry;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Analyzes a batch of reviews with Gemini through Spring AI's ChatClient.
 * One call handles many reviews and returns typed objects instead of free text.
 */
@Service
public class GeminiFeedbackAnalyzer {

    static final List<String> CATEGORIES = List.of(
            "Product Quality", "Customer Service", "Store Experience", "Website/App",
            "Delivery", "Price/Value", "Inventory/Stock", "Other");

    static final String SYSTEM_PROMPT = """
            You are a customer feedback analyst for a retail store.
            Reviews may be written in English or Indonesian, including informal Indonesian and slang
            (for example "gak", "banget", "mantap", "jutek", "kekencengan").
            For every review you receive, return one result with:
            - id: the id of the review
            - language: the ISO 639-1 code of the review's language, for example "en" or "id"
            - sentiment: the overall sentiment of the whole review, exactly one of POSITIVE, NEUTRAL, NEGATIVE
            - category: exactly one of %s (always in English, whatever the review language)
            - actionableInsight: one specific, concise action the store should take (max 30 words),
              written in the same language as the review
            Return one result per review, in the same order, and nothing else.
            """.formatted(String.join(", ", CATEGORIES));

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GeminiFeedbackAnalyzer(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.defaultSystem(SYSTEM_PROMPT).build();
    }

    /**
     * Sends all reviews in one request and maps the JSON answer to {@link AiReview} objects.
     */
    public List<AiReview> analyze(List<FeedbackEntry> reviews) {
        List<ReviewInput> input = reviews.stream()
                .map(r -> new ReviewInput(r.getId(), r.getDepartment(), r.getComment()))
                .toList();

        List<AiReview> results = chatClient.prompt()
                .user(u -> u.text("Analyze these customer reviews:\n{reviews}").param("reviews", toJson(input)))
                .call()
                .entity(new ParameterizedTypeReference<List<AiReview>>() {});

        return results == null ? List.of() : results;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize reviews", e);
        }
    }

    private record ReviewInput(int id, String department, String comment) {
    }
}
