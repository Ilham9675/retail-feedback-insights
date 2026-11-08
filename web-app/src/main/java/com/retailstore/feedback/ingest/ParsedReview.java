package com.retailstore.feedback.ingest;

/**
 * A review read from an uploaded or bundled file, before it is stored.
 *
 * @param label            ground-truth sentiment from the file, or null
 * @param coreNlpSentiment Stanford CoreNLP sentiment, only present in the sentiment-core output format
 */
public record ParsedReview(int reviewId, String customer, String department, String date,
                           String comment, String label, String coreNlpSentiment) {
}
