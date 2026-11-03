package com.retailstore.feedback.model;

/**
 * Gemini's analysis of one review, returned as structured JSON.
 *
 * @param id                the review id it belongs to
 * @param sentiment         POSITIVE, NEUTRAL or NEGATIVE
 * @param category          one of the fixed business categories
 * @param actionableInsight a concrete action the store can take
 */
public record AiReview(int id, String sentiment, String category, String actionableInsight) {
}
