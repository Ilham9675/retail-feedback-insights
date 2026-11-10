package com.retailstore.feedback.service;

import com.retailstore.feedback.model.EnhancedFeedback;
import com.retailstore.feedback.model.ExecutiveSummary;
import com.retailstore.feedback.model.FeedbackEntry;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    @Test
    void summaryPromptAsksForThreeIssuesAndActionsPerDepartment() {
        assertThat(GeminiFeedbackAnalyzer.SUMMARY_PROMPT)
                .contains("topIssues: up to three")
                .contains("priorityActions: up to three")
                .contains("every department")
                .contains("do not invent");
    }

    @Test
    void summarySectionsFollowTheMeasuredNegativeCounts() {
        ExecutiveSummary summary = new ExecutiveSummary("overview", List.of(
                section("Clothing"), section("Misspelled"), section("Online Shopping")));
        Map<String, ExecutiveSummaryService.DepartmentStats> stats = new LinkedHashMap<>();
        stats.put("Online Shopping", new ExecutiveSummaryService.DepartmentStats("Online Shopping", 5, 3));
        stats.put("Clothing", new ExecutiveSummaryService.DepartmentStats("Clothing", 6, 2));

        assertThat(ExecutiveSummaryService.orderByStats(summary, stats))
                .extracting(ExecutiveSummary.DepartmentSummary::department)
                .containsExactly("Online Shopping", "Clothing", "Misspelled");
        assertThat(stats.get("Online Shopping").negativeShare()).isEqualTo(60);
    }

    private static ExecutiveSummary.DepartmentSummary section(String department) {
        return new ExecutiveSummary.DepartmentSummary(department, "summary", List.of(), List.of());
    }

    @Test
    void summaryInputUsesDisplayedSentimentAndShortensLongComments() {
        EnhancedFeedback f = new EnhancedFeedback(new FeedbackEntry(1, "Customer", " ", "2025-04-01", "x".repeat(400), "NEUTRAL"));
        f.setCategory("Delivery");

        GeminiFeedbackAnalyzer.SummaryInput input = ExecutiveSummaryService.toInput(f);

        assertThat(input.department()).isEqualTo("Unknown");
        assertThat(input.sentiment()).isEqualTo("NEUTRAL");
        assertThat(input.comment()).hasSize(ExecutiveSummaryService.MAX_COMMENT_LENGTH + 3).endsWith("...");

        f.setAiSentiment("NEGATIVE");
        assertThat(ExecutiveSummaryService.toInput(f).sentiment()).isEqualTo("NEGATIVE");
    }
}
