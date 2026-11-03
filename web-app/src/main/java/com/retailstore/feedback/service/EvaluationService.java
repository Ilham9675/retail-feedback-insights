package com.retailstore.feedback.service;

import com.retailstore.feedback.model.EnhancedFeedback;
import com.retailstore.feedback.model.EvaluationReport;
import com.retailstore.feedback.model.EvaluationReport.Disagreement;
import com.retailstore.feedback.model.EvaluationReport.ModelScore;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Measures how well Stanford CoreNLP and Gemini agree with the sentiment labels in the raw dataset.
 */
@Service
public class EvaluationService {

    static final List<String> LABELS = List.of("POSITIVE", "NEUTRAL", "NEGATIVE");

    // Raw dataset lines look like: "Feedback #12 [NEGATIVE]"
    private static final Pattern LABEL_PATTERN = Pattern.compile("Feedback #(\\d+)\\s*\\[(\\w+)]");

    private final FeedbackService feedbackService;
    private final String labelsFile;

    public EvaluationService(FeedbackService feedbackService,
                             @Value("${feedback.labels-file:data/store_feedback.txt}") String labelsFile) {
        this.feedbackService = feedbackService;
        this.labelsFile = labelsFile;
    }

    public EvaluationReport evaluate() throws IOException {
        Map<Integer, String> labels = parseLabels(Files.readString(Path.of(labelsFile), StandardCharsets.UTF_8));
        List<EnhancedFeedback> feedback = feedbackService.getEnhancedFeedback();

        Map<Integer, String> coreNlp = new HashMap<>();
        Map<Integer, String> gemini = new HashMap<>();
        for (EnhancedFeedback f : feedback) {
            coreNlp.put(f.getId(), toThreeClass(f.getSentiment()));
            gemini.put(f.getId(), toThreeClass(f.getAiSentiment()));
        }

        List<Disagreement> disagreements = new ArrayList<>();
        for (EnhancedFeedback f : feedback) {
            String expected = labels.get(f.getId());
            if (expected == null) {
                continue;
            }
            String c = coreNlp.get(f.getId());
            String g = gemini.get(f.getId());
            if (!expected.equals(c) || !expected.equals(g)) {
                disagreements.add(new Disagreement(f.getId(), f.getComment(), expected, c, g));
            }
        }

        return new EvaluationReport(
                labels.size(),
                List.of(score("Stanford CoreNLP", labels, coreNlp), score("Gemini", labels, gemini)),
                disagreements,
                feedbackService.getLastProcessingMillis(),
                feedbackService.getLastAiCalls());
    }

    /** Reads "Feedback #id [LABEL]" headers from the raw dataset. */
    static Map<Integer, String> parseLabels(String rawDataset) {
        Map<Integer, String> labels = new LinkedHashMap<>();
        Matcher m = LABEL_PATTERN.matcher(rawDataset);
        while (m.find()) {
            labels.put(Integer.parseInt(m.group(1)), toThreeClass(m.group(2)));
        }
        return labels;
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
