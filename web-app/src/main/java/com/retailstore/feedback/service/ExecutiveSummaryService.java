package com.retailstore.feedback.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.retailstore.feedback.model.EnhancedFeedback;
import com.retailstore.feedback.model.ExecutiveSummary;
import com.retailstore.feedback.model.FeedbackFilter;
import com.retailstore.feedback.persistence.UploadBatch;
import com.retailstore.feedback.persistence.UploadBatchRepository;
import com.retailstore.feedback.service.GeminiFeedbackAnalyzer.SummaryInput;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Per-department executive summary of a batch: main issues and priority actions, written by Gemini.
 * It is generated only when someone asks for it (one request per batch) and stored with the batch.
 */
@Service
public class ExecutiveSummaryService {

    private static final Logger log = LoggerFactory.getLogger(ExecutiveSummaryService.class);
    // Long comments are cut so a 200-review batch still fits comfortably in one request
    static final int MAX_COMMENT_LENGTH = 300;

    private final FeedbackService feedbackService;
    private final GeminiFeedbackAnalyzer geminiAnalyzer;
    private final UploadBatchRepository batchRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ExecutiveSummaryService(FeedbackService feedbackService, GeminiFeedbackAnalyzer geminiAnalyzer,
                                   UploadBatchRepository batchRepository) {
        this.feedbackService = feedbackService;
        this.geminiAnalyzer = geminiAnalyzer;
        this.batchRepository = batchRepository;
    }

    /** The stored summary of a batch, if one was generated. */
    public Optional<ExecutiveSummary> find(Long batchId) {
        String json = feedbackService.getBatch(batchId).getExecutiveSummaryJson();
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, ExecutiveSummary.class));
        } catch (JsonProcessingException e) {
            log.warn("Stored summary of batch {} could not be read: {}", batchId, e.getMessage());
            return Optional.empty();
        }
    }

    /** Asks Gemini for a new summary of an analysed batch and stores it, replacing any earlier one. */
    public synchronized ExecutiveSummary generate(Long batchId) {
        UploadBatch batch = feedbackService.getBatch(batchId);
        if (batch.isBusy()) {
            throw new IllegalStateException("The reviews of this batch are still being analysed.");
        }
        List<SummaryInput> input = feedbackService.getEnhancedFeedback(batch.getId()).stream()
                .map(ExecutiveSummaryService::toInput)
                .toList();
        if (input.isEmpty()) {
            throw new IllegalStateException("This batch has no reviews to summarise.");
        }

        long start = System.currentTimeMillis();
        ExecutiveSummary summary = geminiAnalyzer.summarize(input);
        if (summary == null || summary.departments() == null || summary.departments().isEmpty()) {
            throw new IllegalStateException("Gemini returned an empty summary, please try again.");
        }
        log.info("Batch {}: executive summary for {} departments generated in {} ms",
                batch.getId(), summary.departments().size(), System.currentTimeMillis() - start);

        batch.storeSummary(toJson(summary));
        batchRepository.save(batch);
        return summary;
    }

    /**
     * Review and negative counts per department, computed from the stored results (no API call),
     * shown next to Gemini's text so management sees the size of each problem. Most negative reviews first.
     */
    public Map<String, DepartmentStats> departmentStats(Long batchId) {
        return feedbackService.getEnhancedFeedback(batchId).stream()
                .collect(Collectors.groupingBy(f -> department(f.getDepartment()), Collectors.toList()))
                .entrySet().stream()
                .map(e -> new DepartmentStats(e.getKey(), e.getValue().size(),
                        (int) e.getValue().stream().filter(ExecutiveSummaryService::isNegative).count()))
                .sorted(Comparator.comparingInt(DepartmentStats::negative).reversed()
                        .thenComparing(DepartmentStats::department))
                .collect(Collectors.toMap(DepartmentStats::department, s -> s, (a, b) -> a, LinkedHashMap::new));
    }

    /**
     * Orders Gemini's department sections like {@code stats} (most negative reviews first), so the order follows
     * the measured numbers; departments Gemini named differently go last.
     */
    public static List<ExecutiveSummary.DepartmentSummary> orderByStats(ExecutiveSummary summary,
                                                                        Map<String, DepartmentStats> stats) {
        List<String> order = List.copyOf(stats.keySet());
        return summary.departments().stream()
                .sorted(Comparator.comparingInt(d -> {
                    int index = order.indexOf(d.department());
                    return index < 0 ? Integer.MAX_VALUE : index;
                }))
                .toList();
    }

    static SummaryInput toInput(EnhancedFeedback f) {
        String comment = f.getComment() == null ? "" : f.getComment().strip();
        if (comment.length() > MAX_COMMENT_LENGTH) {
            comment = comment.substring(0, MAX_COMMENT_LENGTH) + "...";
        }
        return new SummaryInput(department(f.getDepartment()), FeedbackFilter.displaySentiment(f), f.getCategory(), comment);
    }

    private static boolean isNegative(EnhancedFeedback f) {
        String sentiment = FeedbackFilter.displaySentiment(f);
        return sentiment != null && sentiment.contains("NEGATIVE");
    }

    private static String department(String department) {
        return department == null || department.isBlank() ? "Unknown" : department;
    }

    private String toJson(ExecutiveSummary summary) {
        try {
            return objectMapper.writeValueAsString(summary);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not store the summary", e);
        }
    }

    /** Counts for one department; {@code negativeShare} is a percentage. */
    public record DepartmentStats(String department, int reviews, int negative) {
        public int negativeShare() {
            return reviews == 0 ? 0 : Math.round(100f * negative / reviews);
        }
    }
}
