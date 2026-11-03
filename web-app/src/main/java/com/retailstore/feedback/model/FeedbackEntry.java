package com.retailstore.feedback.model;

/**
 * Represents a feedback entry with sentiment analysis.
 */
public class FeedbackEntry {
    private int id;
    private String customer;
    private String department;
    private String date;
    private String comment;
    private String sentiment;

    // Default constructor
    public FeedbackEntry() {}

    // Constructor with parameters
    public FeedbackEntry(int id, String customer, String department,
                         String date, String comment, String sentiment) {
        this.id = id;
        this.customer = customer;
        this.department = department;
        this.date = date;
        this.comment = comment;
        this.sentiment = sentiment;
    }

    // Getters and setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getCustomer() { return customer; }
    public void setCustomer(String customer) { this.customer = customer; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public String getSentiment() { return sentiment; }
    public void setSentiment(String sentiment) { this.sentiment = sentiment; }
}
