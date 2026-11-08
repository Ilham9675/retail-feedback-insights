package com.retailstore.feedback.model;

/**
 * Gemini's analysis of one review, returned as structured JSON.
 *
 * @param id                the review id it belongs to
 * @param language          ISO 639-1 code of the review language, e.g. "en" or "id"
 * @param sentiment         POSITIVE, NEUTRAL or NEGATIVE
 * @param category          one of the fixed business categories
 * @param actionableInsight a concrete action the store can take, in the review's language
 */
public record AiReview(int id, String language, String sentiment, String category, String actionableInsight) {
}
