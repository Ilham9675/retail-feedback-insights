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
}
