package com.retailstore.feedback.service;

import com.retailstore.feedback.model.EvaluationReport.ModelScore;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EvaluationServiceTest {

    @Test
    void parsesLabelsFromRawDatasetHeaders() {
        String raw = """
                Feedback #1 [POSITIVE]
                Comment: Great
                Sentiment: POSITIVE

                Feedback #2 [NEGATIVE]
                Comment: Bad
                """;

        assertThat(EvaluationService.parseLabels(raw))
                .containsEntry(1, "POSITIVE")
                .containsEntry(2, "NEGATIVE")
                .hasSize(2);
    }

    @Test
    void collapsesFiveClassSentimentToThree() {
        assertThat(EvaluationService.toThreeClass("Very positive")).isEqualTo("POSITIVE");
        assertThat(EvaluationService.toThreeClass("VERY_NEGATIVE")).isEqualTo("NEGATIVE");
        assertThat(EvaluationService.toThreeClass("Neutral")).isEqualTo("NEUTRAL");
        assertThat(EvaluationService.toThreeClass("garbage")).isNull();
        assertThat(EvaluationService.toThreeClass(null)).isNull();
    }

    @Test
    void scoresAccuracyAndConfusionMatrix() {
        Map<Integer, String> labels = Map.of(1, "POSITIVE", 2, "NEGATIVE", 3, "NEGATIVE", 4, "NEUTRAL");
        Map<Integer, String> predictions = new HashMap<>();
        predictions.put(1, "POSITIVE");
        predictions.put(2, "NEUTRAL");
        predictions.put(3, "NEGATIVE");
        // review 4 has no prediction

        ModelScore score = EvaluationService.score("test", labels, predictions);

        assertThat(score.correct()).isEqualTo(2);
        assertThat(score.total()).isEqualTo(4);
        assertThat(score.accuracy()).isEqualTo(0.5);
        assertThat(score.confusion().get("NEGATIVE").get("NEUTRAL")).isEqualTo(1);
        assertThat(score.confusion().get("NEUTRAL").get("NONE")).isEqualTo(1);
    }
}
