package com.retailstore.feedback.model;

/**
 * Represents feedback enhanced with AI-generated category and actionable insights.
 */
public class EnhancedFeedback extends FeedbackEntry {
    private String category;
    private String actionableInsight;

    // Default constructor
    public EnhancedFeedback() {
        super();
    }

    // Constructor based on FeedbackEntry
    public EnhancedFeedback(FeedbackEntry entry) {
        super(entry.getId(), entry.getCustomer(), entry.getDepartment(),
                entry.getDate(), entry.getComment(), entry.getSentiment());
    }

    // Getters and setters for additional fields
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getActionableInsight() { return actionableInsight; }
    public void setActionableInsight(String actionableInsight) {
        this.actionableInsight = actionableInsight;
    }
}
