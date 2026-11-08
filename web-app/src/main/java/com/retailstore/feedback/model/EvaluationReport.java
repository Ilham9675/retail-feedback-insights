package com.retailstore.feedback.model;

import java.util.List;
import java.util.Map;

/**
 * Compares sentiment predictions from each model against the labels in the dataset.
 *
 * @param batchName        the evaluated batch
 * @param totalLabelled    reviews that have a label in the dataset
 * @param models           one score per model (Stanford CoreNLP when available, Gemini)
 * @param disagreements    reviews where at least one model disagrees with the label
 * @param processingMillis time spent enriching the reviews with Gemini
 * @param aiCalls          number of Gemini requests used
 */
public record EvaluationReport(String batchName,
                               int totalLabelled,
                               List<ModelScore> models,
                               List<Disagreement> disagreements,
                               long processingMillis,
                               int aiCalls) {

    /**
     * @param confusion counts indexed as confusion[expected][predicted]
     */
    public record ModelScore(String model, int correct, int total,
                             Map<String, Map<String, Integer>> confusion) {

        public double accuracy() {
            return total == 0 ? 0 : (double) correct / total;
        }
    }

    public record Disagreement(int id, String comment, String expected, String coreNlp, String gemini) {
    }
}
