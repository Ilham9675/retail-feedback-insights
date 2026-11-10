package com.retailstore.feedback.persistence;

import jakarta.persistence.Column;
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

    // Background analysis state; column defaults let existing databases add these columns
    @Column(columnDefinition = "boolean default false")
    private boolean analyzing;
    @Column(columnDefinition = "integer default 0")
    private int processedReviews;

    protected UploadBatch() {
    }

    public UploadBatch(String name, int reviewCount) {
        this.name = name;
        this.reviewCount = reviewCount;
        this.createdAt = Instant.now();
    }

    public void startAnalysis(int reviewsToProcess) {
        this.analyzing = true;
        this.processedReviews = reviewCount - reviewsToProcess;
    }

    public void recordProgress(int processedReviews) {
        this.processedReviews = processedReviews;
    }

    /** Clears a stale "analyzing" flag, e.g. when the app stopped during an analysis. */
    public void stopAnalysis() {
        this.analyzing = false;
    }

    public void markAnalyzed(int aiCalls, long processingMillis, int failedReviews) {
        this.analyzed = true;
        this.analyzing = false;
        this.processedReviews = reviewCount;
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

    public boolean isAnalyzing() { return analyzing; }

    public int getProcessedReviews() { return processedReviews; }

    /** True while results are missing or being (re)computed, so pages should show progress. */
    public boolean isBusy() { return !analyzed || analyzing; }
}
