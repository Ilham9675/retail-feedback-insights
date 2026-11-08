package com.retailstore.feedback.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiFeedbackAnalyzerTest {

    @Test
    void promptCoversIndonesianReviewsAndTheFixedCategories() {
        assertThat(GeminiFeedbackAnalyzer.SYSTEM_PROMPT)
                .contains("Indonesian")
                .contains("same language as the review")
                .contains("ISO 639-1");
        GeminiFeedbackAnalyzer.CATEGORIES.forEach(c -> assertThat(GeminiFeedbackAnalyzer.SYSTEM_PROMPT).contains(c));
    }
}
