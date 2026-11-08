package com.retailstore.feedback.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import java.time.Instant;

/**
 * A set of reviews imported together: the bundled sample dataset or one uploaded file.
 */
@Entity
public class UploadBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private Instant createdAt;
    private int reviewCount;

    // Set once Gemini has processed the batch, so results are reused instead of calling the API again
    private boolean analyzed;
    private int aiCalls;
    private long processingMillis;
    private int failedReviews;

    protected UploadBatch() {
    }

    public UploadBatch(String name, int reviewCount) {
        this.name = name;
        this.reviewCount = reviewCount;
        this.createdAt = Instant.now();
    }

    public void markAnalyzed(int aiCalls, long processingMillis, int failedReviews) {
        this.analyzed = true;
        this.aiCalls = aiCalls;
        this.processingMillis = processingMillis;
        this.failedReviews = failedReviews;
    }

    public Long getId() { return id; }

    public String getName() { return name; }

    public Instant getCreatedAt() { return createdAt; }

    public int getReviewCount() { return reviewCount; }

    public boolean isAnalyzed() { return analyzed; }

    public int getAiCalls() { return aiCalls; }

    public long getProcessingMillis() { return processingMillis; }

    public int getFailedReviews() { return failedReviews; }
}
