package com.retailstore.feedback.service;

import com.retailstore.feedback.model.AiReview;
import com.retailstore.feedback.model.EnhancedFeedback;
import com.retailstore.feedback.model.FeedbackEntry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Uses the real sample data with a mocked Gemini analyzer, so no API calls are made.
 */
class FeedbackServiceTest {

    // Tests run from the web-app module directory
    private static final String SAMPLE_OUTPUT = "../data/sentiment_feedback_output.txt";

    private GeminiFeedbackAnalyzer analyzer;
    private FeedbackService service;

    @BeforeEach
    void setUp() {
        analyzer = mock(GeminiFeedbackAnalyzer.class);
        service = new FeedbackService();
        ReflectionTestUtils.setField(service, "geminiAnalyzer", analyzer);
        ReflectionTestUtils.setField(service, "sentimentFilePath", SAMPLE_OUTPUT);
        ReflectionTestUtils.setField(service, "batchSize", 25);
    }

    @Test
    void readsAllEntriesAndNormalizesCoreNlpSentiment() throws Exception {
        List<FeedbackEntry> entries = service.readFeedbackData();

        assertThat(entries).hasSize(50);
        assertThat(entries.get(0).getId()).isEqualTo(1);
        assertThat(entries.get(0).getDepartment()).isEqualTo("Returns");
        assertThat(entries).extracting(FeedbackEntry::getSentiment)
                .allMatch(s -> s.equals(s.toUpperCase()));
    }

    @Test
    void sendsReviewsInBatchesAndMapsResultsById() throws Exception {
        when(analyzer.analyze(anyList())).thenAnswer(invocation -> {
            List<FeedbackEntry> batch = invocation.getArgument(0);
            return batch.stream()
                    .map(e -> new AiReview(e.getId(), "negative", "Store Experience", "Fix it"))
                    .toList();
        });

        List<EnhancedFeedback> result = service.getEnhancedFeedback();

        verify(analyzer, times(2)).analyze(anyList());
        assertThat(service.getLastAiCalls()).isEqualTo(2);
        assertThat(result).hasSize(50);
        assertThat(result.get(0).getAiSentiment()).isEqualTo("NEGATIVE");
        assertThat(result.get(0).getCategory()).isEqualTo("Store Experience");
    }

    @Test
    void unknownCategoryFallsBackToOther() throws Exception {
        when(analyzer.analyze(anyList())).thenAnswer(invocation -> {
            List<FeedbackEntry> batch = invocation.getArgument(0);
            return batch.stream()
                    .map(e -> new AiReview(e.getId(), "POSITIVE", "Checkout", "Keep it up"))
                    .toList();
        });

        assertThat(service.getEnhancedFeedback()).allMatch(f -> f.getCategory().equals("Other"));
    }

    @Test
    void failedBatchLeavesReviewsUncategorizedInsteadOfFailing() throws Exception {
        when(analyzer.analyze(anyList())).thenThrow(new RuntimeException("quota exceeded"));

        List<EnhancedFeedback> result = service.getEnhancedFeedback();

        assertThat(result).hasSize(50)
                .allMatch(f -> f.getCategory().equals("Uncategorized"))
                .allMatch(f -> f.getActionableInsight().contains("quota exceeded"));
    }

    @Test
    void resultsAreCachedAfterFirstCall() throws Exception {
        when(analyzer.analyze(anyList())).thenReturn(List.of());

        service.getEnhancedFeedback();
        service.getEnhancedFeedback();

        verify(analyzer, times(2)).analyze(anyList());
    }
}
