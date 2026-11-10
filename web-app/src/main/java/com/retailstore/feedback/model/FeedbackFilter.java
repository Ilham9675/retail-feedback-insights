package com.retailstore.feedback.model;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Dashboard filters; a null field means "no filter". Sentiment is matched against the displayed sentiment
 * (Gemini, falling back to CoreNLP).
 */
public record FeedbackFilter(String department, String sentiment, String category, LocalDate from, LocalDate to) {

    public static final FeedbackFilter NONE = new FeedbackFilter(null, null, null, null, null);

    /** Builds a filter from request parameters, treating blank values as "no filter". */
    public static FeedbackFilter of(String department, String sentiment, String category, LocalDate from, LocalDate to) {
        return new FeedbackFilter(blankToNull(department), blankToNull(sentiment), blankToNull(category), from, to);
    }

    public boolean isActive() {
        return department != null || sentiment != null || category != null || from != null || to != null;
    }

    public boolean matches(EnhancedFeedback f) {
        if (department != null && !department.equals(f.getDepartment())) {
            return false;
        }
        if (sentiment != null && !sentiment.equals(displaySentiment(f))) {
            return false;
        }
        if (category != null && !category.equals(f.getCategory())) {
            return false;
        }
        if (from != null || to != null) {
            // Reviews without a usable date are left out once a date range is chosen
            LocalDate date = parseDate(f.getDate());
            if (date == null || (from != null && date.isBefore(from)) || (to != null && date.isAfter(to))) {
                return false;
            }
        }
        return true;
    }

    /** Gemini's sentiment, or CoreNLP's when Gemini has none. */
    public static String displaySentiment(EnhancedFeedback f) {
        return f.getAiSentiment() != null ? f.getAiSentiment() : f.getSentiment();
    }

    /** Parses a review date in YYYY-MM-DD form; anything else returns null. */
    public static LocalDate parseDate(String date) {
        if (date == null || date.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(date.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
