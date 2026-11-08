package com.retailstore.feedback.service;

import com.retailstore.feedback.model.EvaluationReport;
import com.retailstore.feedback.model.EvaluationReport.Disagreement;
import com.retailstore.feedback.model.EvaluationReport.ModelScore;
import com.retailstore.feedback.persistence.FeedbackRecord;
import com.retailstore.feedback.persistence.UploadBatch;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Measures how well Stanford CoreNLP and Gemini agree with the sentiment labels of a batch.
 * Only reviews that came with a label are scored.
 */
@Service
public class EvaluationService {

    static final List<String> LABELS = List.of("POSITIVE", "NEUTRAL", "NEGATIVE");

    private final FeedbackService feedbackService;

    public EvaluationService(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    public EvaluationReport evaluate(Long batchId) {
        List<FeedbackRecord> records = feedbackService.getAnalyzedRecords(batchId);
        UploadBatch batch = feedbackService.getBatch(batchId);

        Map<Integer, String> labels = new LinkedHashMap<>();
        Map<Integer, String> coreNlp = new HashMap<>();
        Map<Integer, String> gemini = new HashMap<>();
        for (FeedbackRecord r : records) {
            if (r.getLabel() != null) {
                labels.put(r.getReviewId(), toThreeClass(r.getLabel()));
            }
            coreNlp.put(r.getReviewId(), toThreeClass(r.getCoreNlpSentiment()));
            gemini.put(r.getReviewId(), toThreeClass(r.getAiSentiment()));
        }

        // CoreNLP results only exist for the bundled sample, so it is only scored when present
        boolean hasCoreNlp = records.stream().anyMatch(r -> r.getCoreNlpSentiment() != null);
        List<ModelScore> models = new ArrayList<>();
        if (hasCoreNlp) {
            models.add(score("Stanford CoreNLP", labels, coreNlp));
        }
        models.add(score("Gemini", labels, gemini));

        List<Disagreement> disagreements = new ArrayList<>();
        for (FeedbackRecord r : records) {
            String expected = labels.get(r.getReviewId());
            if (expected == null) {
                continue;
            }
            String c = coreNlp.get(r.getReviewId());
            String g = gemini.get(r.getReviewId());
            if ((hasCoreNlp && !expected.equals(c)) || !expected.equals(g)) {
                disagreements.add(new Disagreement(r.getReviewId(), r.getComment(), expected, c, g));
            }
        }

        return new EvaluationReport(batch.getName(), labels.size(), models, disagreements,
                batch.getProcessingMillis(), batch.getAiCalls());
    }

    /** Scores predictions against labels; a missing prediction counts as wrong. */
    static ModelScore score(String model, Map<Integer, String> labels, Map<Integer, String> predictions) {
        Map<String, Map<String, Integer>> confusion = new LinkedHashMap<>();
        for (String expected : LABELS) {
            Map<String, Integer> row = new LinkedHashMap<>();
            LABELS.forEach(predicted -> row.put(predicted, 0));
            row.put("NONE", 0);
            confusion.put(expected, row);
        }

        int correct = 0;
        for (Map.Entry<Integer, String> label : labels.entrySet()) {
            String predicted = Objects.requireNonNullElse(predictions.get(label.getKey()), "NONE");
            confusion.get(label.getValue()).merge(predicted, 1, Integer::sum);
            if (predicted.equals(label.getValue())) {
                correct++;
            }
        }
        return new ModelScore(model, correct, labels.size(), confusion);
    }

    /** Collapses CoreNLP's five classes into the dataset's three; unknown values become null. */
    static String toThreeClass(String sentiment) {
        if (sentiment == null) {
            return null;
        }
        String s = sentiment.trim().toUpperCase().replace(' ', '_');
        if (s.endsWith("POSITIVE")) {
            return "POSITIVE";
        }
        if (s.endsWith("NEGATIVE")) {
            return "NEGATIVE";
        }
        return s.equals("NEUTRAL") ? "NEUTRAL" : null;
    }
}
