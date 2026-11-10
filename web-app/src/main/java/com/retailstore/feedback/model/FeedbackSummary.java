package com.retailstore.feedback.model;

import java.util.List;
import java.util.Map;

/**
 * Represents a summary of feedback data for the dashboard.
 */
public class FeedbackSummary {
    private int totalFeedback;
    private Map<String, Integer> sentimentCounts;
    private Map<String, Integer> categoryCounts;
    private Map<String, Integer> departmentCounts;
    private List<EnhancedFeedback> recentFeedback;

    // Before filtering, plus the values available in the filter dropdowns
    private int unfilteredTotal;
    private List<String> departments;
    private List<String> sentiments;
    private List<String> categories;

    // Weekly sentiment trend: week start dates (YYYY-MM-DD) and one count series per sentiment
    private List<String> trendWeeks;
    private Map<String, List<Integer>> trendSeries;

    // Default constructor
    public FeedbackSummary() {}

    // Getters and setters
    public int getTotalFeedback() { return totalFeedback; }
    public void setTotalFeedback(int totalFeedback) {
        this.totalFeedback = totalFeedback;
    }

    public Map<String, Integer> getSentimentCounts() { return sentimentCounts; }
    public void setSentimentCounts(Map<String, Integer> sentimentCounts) {
        this.sentimentCounts = sentimentCounts;
    }

    public Map<String, Integer> getCategoryCounts() { return categoryCounts; }
    public void setCategoryCounts(Map<String, Integer> categoryCounts) {
        this.categoryCounts = categoryCounts;
    }

    public Map<String, Integer> getDepartmentCounts() { return departmentCounts; }
    public void setDepartmentCounts(Map<String, Integer> departmentCounts) {
        this.departmentCounts = departmentCounts;
    }

    public List<EnhancedFeedback> getRecentFeedback() { return recentFeedback; }
    public void setRecentFeedback(List<EnhancedFeedback> recentFeedback) {
        this.recentFeedback = recentFeedback;
    }

    public int getUnfilteredTotal() { return unfilteredTotal; }
    public void setUnfilteredTotal(int unfilteredTotal) { this.unfilteredTotal = unfilteredTotal; }

    public List<String> getDepartments() { return departments; }
    public void setDepartments(List<String> departments) { this.departments = departments; }

    public List<String> getSentiments() { return sentiments; }
    public void setSentiments(List<String> sentiments) { this.sentiments = sentiments; }

    public List<String> getCategories() { return categories; }
    public void setCategories(List<String> categories) { this.categories = categories; }

    public List<String> getTrendWeeks() { return trendWeeks; }
    public void setTrendWeeks(List<String> trendWeeks) { this.trendWeeks = trendWeeks; }

    public Map<String, List<Integer>> getTrendSeries() { return trendSeries; }
    public void setTrendSeries(Map<String, List<Integer>> trendSeries) { this.trendSeries = trendSeries; }
}
