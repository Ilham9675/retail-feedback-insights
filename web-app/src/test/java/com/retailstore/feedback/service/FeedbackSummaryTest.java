package com.retailstore.feedback.service;

import com.retailstore.feedback.model.EnhancedFeedback;
import com.retailstore.feedback.model.FeedbackEntry;
import com.retailstore.feedback.model.FeedbackFilter;
import com.retailstore.feedback.model.FeedbackSummary;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FeedbackSummaryTest {

    private static EnhancedFeedback review(int id, String department, String date, String coreNlp, String ai, String category) {
        EnhancedFeedback f = new EnhancedFeedback(new FeedbackEntry(id, "Customer", department, date, "Comment", coreNlp));
        f.setAiSentiment(ai);
        f.setCategory(category);
        return f;
    }

    private final List<EnhancedFeedback> reviews = List.of(
            review(1, "Returns", "2025-04-01", "NEUTRAL", "NEGATIVE", "Customer Service"),   // Tue, week of 03-31
            review(2, "Returns", "2025-04-06", "POSITIVE", "POSITIVE", "Store Experience"),  // Sun, same week
            review(3, "Grocery", "2025-04-16", "VERY_NEGATIVE", null, "Product Quality"),   // week of 04-14
            review(4, "Grocery", "not a date", "NEUTRAL", "NEUTRAL", "Other"));

    @Test
    void filterMatchesOnDepartmentDisplayedSentimentCategoryAndDates() {
        assertThat(reviews.stream().filter(FeedbackFilter.of("Returns", "", null, null, null)::matches))
                .extracting(EnhancedFeedback::getId).containsExactly(1, 2);
        // Gemini's sentiment wins over CoreNLP's; CoreNLP is used only when Gemini has none
        assertThat(reviews.stream().filter(FeedbackFilter.of(null, "NEGATIVE", null, null, null)::matches))
                .extracting(EnhancedFeedback::getId).containsExactly(1);
        assertThat(reviews.stream().filter(FeedbackFilter.of(null, "VERY_NEGATIVE", null, null, null)::matches))
                .extracting(EnhancedFeedback::getId).containsExactly(3);
        assertThat(reviews.stream().filter(FeedbackFilter.of(null, null, "Other", null, null)::matches))
                .extracting(EnhancedFeedback::getId).containsExactly(4);
        // Date ranges are inclusive and drop reviews without a usable date
        assertThat(reviews.stream().filter(FeedbackFilter.of(null, null, null,
                        LocalDate.parse("2025-04-06"), LocalDate.parse("2025-04-16"))::matches))
                .extracting(EnhancedFeedback::getId).containsExactly(2, 3);
    }

    @Test
    void blankParametersMeanNoFilter() {
        FeedbackFilter filter = FeedbackFilter.of(" ", "", null, null, null);

        assertThat(filter.isActive()).isFalse();
        assertThat(reviews).allMatch(filter::matches);
    }

    @Test
    void weeklyTrendGroupsByMondayAndFillsEmptyWeeks() {
        FeedbackSummary summary = new FeedbackSummary();

        FeedbackService.addWeeklyTrend(summary, reviews);

        assertThat(summary.getTrendWeeks()).containsExactly("2025-03-31", "2025-04-07", "2025-04-14");
        assertThat(summary.getTrendSeries().get("POSITIVE")).containsExactly(1, 0, 0);
        assertThat(summary.getTrendSeries().get("NEGATIVE")).containsExactly(1, 0, 1);
        assertThat(summary.getTrendSeries().get("NEUTRAL")).containsExactly(0, 0, 0);
    }

    @Test
    void trendIsEmptyWithoutDatedReviews() {
        FeedbackSummary summary = new FeedbackSummary();

        FeedbackService.addWeeklyTrend(summary, List.of(reviews.get(3)));

        assertThat(summary.getTrendWeeks()).isEmpty();
    }
}
