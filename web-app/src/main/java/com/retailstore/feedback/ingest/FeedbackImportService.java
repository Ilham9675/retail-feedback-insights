package com.retailstore.feedback.ingest;

import com.retailstore.feedback.persistence.FeedbackRecord;
import com.retailstore.feedback.persistence.FeedbackRecordRepository;
import com.retailstore.feedback.persistence.UploadBatch;
import com.retailstore.feedback.persistence.UploadBatchRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Stores parsed reviews as a new batch.
 */
@Service
public class FeedbackImportService {

    private final FeedbackFileParser parser;
    private final UploadBatchRepository batchRepository;
    private final FeedbackRecordRepository recordRepository;
    private final int maxReviews;

    public FeedbackImportService(FeedbackFileParser parser,
                                 UploadBatchRepository batchRepository,
                                 FeedbackRecordRepository recordRepository,
                                 @Value("${feedback.upload.max-reviews:200}") int maxReviews) {
        this.parser = parser;
        this.batchRepository = batchRepository;
        this.recordRepository = recordRepository;
        this.maxReviews = maxReviews;
    }

    /** Parses an uploaded file and stores it; throws {@link InvalidFeedbackFileException} with a user-facing message. */
    public UploadBatch importFile(String fileName, String content) {
        return importReviews(fileName, parser.parse(fileName, content));
    }

    @Transactional
    public UploadBatch importReviews(String batchName, List<ParsedReview> reviews) {
        // Every review costs Gemini quota, so uploads are capped
        if (reviews.size() > maxReviews) {
            throw new InvalidFeedbackFileException("The file has " + reviews.size()
                    + " reviews; the limit is " + maxReviews + " per upload.");
        }
        UploadBatch batch = batchRepository.save(new UploadBatch(batchName, reviews.size()));
        recordRepository.saveAll(reviews.stream()
                .map(r -> new FeedbackRecord(batch, r.reviewId(), r.customer(), r.department(), r.date(),
                        r.comment(), r.label(), r.coreNlpSentiment()))
                .toList());
        return batch;
    }
}
