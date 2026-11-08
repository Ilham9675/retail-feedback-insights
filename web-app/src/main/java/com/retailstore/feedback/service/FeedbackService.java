package com.retailstore.feedback.service;

import com.retailstore.feedback.model.AiReview;
import com.retailstore.feedback.model.EnhancedFeedback;
import com.retailstore.feedback.model.FeedbackEntry;
import com.retailstore.feedback.model.FeedbackSummary;
import com.retailstore.feedback.persistence.FeedbackRecord;
import com.retailstore.feedback.persistence.FeedbackRecordRepository;
import com.retailstore.feedback.persistence.UploadBatch;
import com.retailstore.feedback.persistence.UploadBatchRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Reads stored review batches and enriches them with Gemini's sentiment, category and actionable insight.
 * A batch is analysed once; the results are stored so later page views don't call the API again.
 */
@Service
public class FeedbackService {

    private static final Logger log = LoggerFactory.getLogger(FeedbackService.class);
    static final String UNCATEGORIZED = "Uncategorized";

    private final GeminiFeedbackAnalyzer geminiAnalyzer;
    private final UploadBatchRepository batchRepository;
    private final FeedbackRecordRepository recordRepository;
    private final int batchSize;

    public FeedbackService(GeminiFeedbackAnalyzer geminiAnalyzer,
                           UploadBatchRepository batchRepository,
                           FeedbackRecordRepository recordRepository,
                           // Number of reviews sent to Gemini in one request
                           @Value("${feedback.ai.batch-size:25}") int batchSize) {
        this.geminiAnalyzer = geminiAnalyzer;
        this.batchRepository = batchRepository;
        this.recordRepository = recordRepository;
        this.batchSize = batchSize;
    }

    public List<UploadBatch> listBatches() {
        return batchRepository.findAllByOrderByCreatedAtDesc();
    }

    /** The requested batch, or the most recent one when {@code batchId} is null. */
    public UploadBatch getBatch(Long batchId) {
        return (batchId == null ? batchRepository.findFirstByOrderByCreatedAtDesc() : batchRepository.findById(batchId))
                .orElseThrow(() -> new NoSuchElementException(batchId == null
                        ? "No feedback has been imported yet."
                        : "Feedback batch " + batchId + " does not exist."));
    }

    /** Stored reviews of a batch, analysed with Gemini first if that hasn't happened yet. */
    public synchronized List<FeedbackRecord> getAnalyzedRecords(Long batchId) {
        UploadBatch batch = getBatch(batchId);
        List<FeedbackRecord> records = recordRepository.findByBatchIdOrderByReviewId(batch.getId());
        if (!batch.isAnalyzed()) {
            analyze(batch, records, records);
        }
        return records;
    }

    /** Runs Gemini again for the reviews of a batch that have no result yet (for example after a quota error). */
    public synchronized void reanalyzeFailed(Long batchId) {
        UploadBatch batch = getBatch(batchId);
        List<FeedbackRecord> records = recordRepository.findByBatchIdOrderByReviewId(batch.getId());
        List<FeedbackRecord> pending = records.stream().filter(r -> r.getAiSentiment() == null).toList();
        if (!pending.isEmpty()) {
            analyze(batch, records, pending);
        }
    }

    public List<EnhancedFeedback> getEnhancedFeedback(Long batchId) {
        return getAnalyzedRecords(batchId).stream().map(FeedbackService::toEnhancedFeedback).toList();
    }

    private void analyze(UploadBatch batch, List<FeedbackRecord> all, List<FeedbackRecord> pending) {
        long start = System.currentTimeMillis();
        int calls = 0;

        for (int from = 0; from < pending.size(); from += batchSize) {
            List<FeedbackRecord> chunk = pending.subList(from, Math.min(from + batchSize, pending.size()));
            log.info("Batch {}: analyzing reviews {}-{} of {} with Gemini",
                    batch.getId(), from + 1, from + chunk.size(), pending.size());

            Map<Integer, AiReview> resultsById = new HashMap<>();
            String failure = null;
            try {
                calls++;
                List<FeedbackEntry> input = chunk.stream().map(FeedbackService::toEntry).toList();
                for (AiReview review : geminiAnalyzer.analyze(input)) {
                    resultsById.put(review.id(), review);
                }
            } catch (Exception e) {
                log.warn("Gemini request for batch {} failed: {}", batch.getId(), e.getMessage());
                failure = e.getMessage();
            }

            for (FeedbackRecord record : chunk) {
                apply(record, resultsById.get(record.getReviewId()), failure);
            }
        }

        recordRepository.saveAll(pending);
        int failed = (int) all.stream().filter(r -> r.getAiSentiment() == null).count();
        long elapsed = System.currentTimeMillis() - start;
        batch.markAnalyzed(calls, elapsed, failed);
        batchRepository.save(batch);
        log.info("Batch {}: {} review(s) analyzed with {} Gemini call(s) in {} ms, {} without a result",
                batch.getId(), pending.size(), calls, elapsed, failed);
    }

    private static void apply(FeedbackRecord record, AiReview review, String failure) {
        if (review == null) {
            record.applyAnalysis(null, UNCATEGORIZED, failure == null
                    ? "Gemini returned no result for this review."
                    : "Could not generate insight due to API error: " + failure);
            return;
        }
        // The model occasionally invents a category; keep the dashboard to the fixed list
        String category = GeminiFeedbackAnalyzer.CATEGORIES.contains(review.category()) ? review.category() : "Other";
        record.applyAnalysis(normalizeSentiment(review.sentiment()), category, review.actionableInsight());
    }

    /** "Very positive" / "positive" -> "VERY_POSITIVE" / "POSITIVE". */
    static String normalizeSentiment(String sentiment) {
        return sentiment == null ? null : sentiment.trim().toUpperCase().replace(' ', '_');
    }

    private static FeedbackEntry toEntry(FeedbackRecord r) {
        return new FeedbackEntry(r.getReviewId(), r.getCustomer(), r.getDepartment(), r.getReviewDate(),
                r.getComment(), r.getCoreNlpSentiment());
    }

    static EnhancedFeedback toEnhancedFeedback(FeedbackRecord r) {
        EnhancedFeedback feedback = new EnhancedFeedback(toEntry(r));
        feedback.setAiSentiment(r.getAiSentiment());
        feedback.setCategory(r.getCategory());
        feedback.setActionableInsight(r.getActionableInsight());
        return feedback;
    }

    /**
     * Generates a summary of a batch for the dashboard.
     * Sentiment counts use Gemini's result, falling back to CoreNLP where Gemini has none.
     */
    public FeedbackSummary generateFeedbackSummary(Long batchId) {
        List<EnhancedFeedback> allFeedback = getEnhancedFeedback(batchId);
        FeedbackSummary summary = new FeedbackSummary();
        summary.setTotalFeedback(allFeedback.size());
        summary.setSentimentCounts(countBy(allFeedback,
                f -> f.getAiSentiment() != null ? f.getAiSentiment() : orUnknown(f.getSentiment())));
        summary.setCategoryCounts(countBy(allFeedback, f -> orUnknown(f.getCategory())));
        summary.setDepartmentCounts(countBy(allFeedback, f -> orUnknown(f.getDepartment())));

        // Most recent = highest review ids
        summary.setRecentFeedback(allFeedback.stream()
                .sorted(Comparator.comparing(EnhancedFeedback::getId).reversed())
                .limit(5)
                .collect(Collectors.toList()));
        return summary;
    }

    private static Map<String, Integer> countBy(List<EnhancedFeedback> feedback, Function<EnhancedFeedback, String> key) {
        Map<String, Integer> counts = new HashMap<>();
        for (EnhancedFeedback f : feedback) {
            counts.merge(key.apply(f), 1, Integer::sum);
        }
        return counts;
    }

    private static String orUnknown(String value) {
        return value == null || value.isBlank() ? "Unknown" : value;
    }
}
