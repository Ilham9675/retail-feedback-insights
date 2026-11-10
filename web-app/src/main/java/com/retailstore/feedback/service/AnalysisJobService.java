package com.retailstore.feedback.service;

import com.retailstore.feedback.persistence.UploadBatch;
import com.retailstore.feedback.persistence.UploadBatchRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs Gemini analysis in the background so pages can show progress instead of waiting.
 * A single worker thread processes one batch at a time, which also keeps the request rate
 * within the Gemini free tier.
 */
@Service
@Order(0)
public class AnalysisJobService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AnalysisJobService.class);

    private final FeedbackService feedbackService;
    private final UploadBatchRepository batchRepository;
    private final ThreadPoolTaskExecutor executor;

    // Batches queued or running, so repeated page loads don't queue the same work twice
    private final Set<Long> scheduled = ConcurrentHashMap.newKeySet();

    public AnalysisJobService(FeedbackService feedbackService, UploadBatchRepository batchRepository) {
        this.feedbackService = feedbackService;
        this.batchRepository = batchRepository;
        this.executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setThreadNamePrefix("gemini-analysis-");
        executor.initialize();
    }

    /** Clears "analyzing" flags left behind if the app stopped in the middle of an analysis. */
    @Override
    public void run(ApplicationArguments args) {
        for (UploadBatch batch : batchRepository.findByAnalyzingTrue()) {
            batch.stopAnalysis();
            batchRepository.save(batch);
        }
    }

    /** Starts analysing a batch in the background unless it is already analysed or scheduled. */
    public void startAnalysis(long batchId) {
        if (feedbackService.getBatch(batchId).isAnalyzed()) {
            return;
        }
        schedule(batchId, () -> feedbackService.getAnalyzedRecords(batchId));
    }

    /** Re-sends the reviews of a batch that have no Gemini result, in the background. */
    public void startRetry(long batchId) {
        schedule(batchId, () -> feedbackService.reanalyzeFailed(batchId));
    }

    private void schedule(long batchId, Runnable work) {
        if (!scheduled.add(batchId)) {
            return;
        }
        executor.execute(() -> {
            try {
                work.run();
            } catch (Exception e) {
                log.error("Background analysis of batch {} failed", batchId, e);
                batchRepository.findById(batchId).ifPresent(b -> {
                    b.stopAnalysis();
                    batchRepository.save(b);
                });
            } finally {
                scheduled.remove(batchId);
            }
        });
    }

    /** True while a batch is queued, being analysed, or has no results yet. */
    public boolean isBusy(UploadBatch batch) {
        return batch.isBusy() || scheduled.contains(batch.getId());
    }

    /** Progress snapshot for the status endpoint and the progress page. */
    public Map<String, Object> status(long batchId) {
        UploadBatch batch = feedbackService.getBatch(batchId);
        return Map.of(
                "batchId", batch.getId(),
                "name", batch.getName(),
                "analyzed", !isBusy(batch),
                // False while not analysed means the job stopped (e.g. an unexpected error); the page offers a retry
                "running", batch.isAnalyzing() || scheduled.contains(batchId),
                "processedReviews", batch.getProcessedReviews(),
                "reviewCount", batch.getReviewCount(),
                "failedReviews", batch.getFailedReviews());
    }
}
