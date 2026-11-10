package com.retailstore.feedback.ingest;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * Gemini results for the bundled samples, exported from a real run ({@code demo/precomputed-results.json}).
 * Applied when a sample is imported, so a fresh database (or the public demo) shows analysed samples
 * without an API key and without spending quota.
 */
public record PrecomputedResults(String generatedBy, String generatedOn, List<Batch> batches) {

    public record Batch(String name, int aiCalls, long processingMillis, List<Review> results, JsonNode summary) {
    }

    public record Review(int reviewId, String language, String aiSentiment, String category, String actionableInsight) {
    }
}
