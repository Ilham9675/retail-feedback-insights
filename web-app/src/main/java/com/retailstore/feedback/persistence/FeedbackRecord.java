package com.retailstore.feedback.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * One customer review in a batch, together with its sentiment and AI analysis.
 */
@Entity
@Table(indexes = @Index(columnList = "batch_id"))
public class FeedbackRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id")
    private UploadBatch batch;

    // Review number within its batch ("Feedback #12" or the CSV id column)
    private int reviewId;
    private String customer;
    private String department;
    private String reviewDate;

    @Column(length = 2000)
    private String comment;

    // Ground-truth label from the file, if it had one (POSITIVE / NEUTRAL / NEGATIVE)
    private String label;
    // Stanford CoreNLP result; only the bundled sample has it
    private String coreNlpSentiment;

    // Filled in by Gemini
    private String language;
    private String aiSentiment;
    private String category;
    @Column(length = 1000)
    private String actionableInsight;

    protected FeedbackRecord() {
    }

    public FeedbackRecord(UploadBatch batch, int reviewId, String customer, String department, String reviewDate,
                          String comment, String label, String coreNlpSentiment) {
        this.batch = batch;
        this.reviewId = reviewId;
        this.customer = customer;
        this.department = department;
        this.reviewDate = reviewDate;
        this.comment = comment;
        this.label = label;
        this.coreNlpSentiment = coreNlpSentiment;
    }

    public void applyAnalysis(String language, String aiSentiment, String category, String actionableInsight) {
        this.language = language;
        this.aiSentiment = aiSentiment;
        this.category = category;
        this.actionableInsight = actionableInsight;
    }

    public Long getId() { return id; }

    public UploadBatch getBatch() { return batch; }

    public int getReviewId() { return reviewId; }

    public String getCustomer() { return customer; }

    public String getDepartment() { return department; }

    public String getReviewDate() { return reviewDate; }

    public String getComment() { return comment; }

    public String getLabel() { return label; }

    public String getCoreNlpSentiment() { return coreNlpSentiment; }

    public String getLanguage() { return language; }

    public String getAiSentiment() { return aiSentiment; }

    public String getCategory() { return category; }

    public String getActionableInsight() { return actionableInsight; }
}
