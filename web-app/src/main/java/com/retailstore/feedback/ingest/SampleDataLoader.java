package com.retailstore.feedback.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.retailstore.feedback.persistence.FeedbackRecord;
import com.retailstore.feedback.persistence.FeedbackRecordRepository;
import com.retailstore.feedback.persistence.UploadBatch;
import com.retailstore.feedback.persistence.UploadBatchRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Imports the bundled samples once (skipped when a batch with the same name already exists):
 * <ul>
 *   <li>the 30-review Indonesian sample with labels, to show non-English support</li>
 *   <li>the 50-review English sample: CoreNLP results from the sentiment-core output plus the labels of the raw dataset</li>
 * </ul>
 * The English sample is imported last so it is the default batch on a fresh database.
 * When {@code feedback.sample.precomputed} is on, stored Gemini results from a real run are applied right after the import,
 * so the samples are analysed without an API key (used by the public demo).
 */
@Component
public class SampleDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SampleDataLoader.class);

    private final FeedbackFileParser parser;
    private final FeedbackImportService importService;
    private final UploadBatchRepository batchRepository;
    private final FeedbackRecordRepository recordRepository;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final boolean precomputed;
    private final String coreNlpOutputFile;
    private final String labelsFile;

    public SampleDataLoader(FeedbackFileParser parser,
                            FeedbackImportService importService,
                            UploadBatchRepository batchRepository,
                            FeedbackRecordRepository recordRepository,
                            ObjectMapper objectMapper,
                            @Value("${feedback.sample.enabled:true}") boolean enabled,
                            @Value("${feedback.sample.precomputed:true}") boolean precomputed,
                            @Value("${feedback.input-file:data/sentiment_feedback_output.txt}") String coreNlpOutputFile,
                            @Value("${feedback.labels-file:data/store_feedback.txt}") String labelsFile) {
        this.parser = parser;
        this.importService = importService;
        this.batchRepository = batchRepository;
        this.recordRepository = recordRepository;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.precomputed = precomputed;
        this.coreNlpOutputFile = coreNlpOutputFile;
        this.labelsFile = labelsFile;
    }

    static final String INDONESIAN_SAMPLE = "static/samples/sample_reviews_id.csv";
    static final String PRECOMPUTED_RESULTS = "demo/precomputed-results.json";

    @Override
    public void run(ApplicationArguments args) throws IOException {
        if (!enabled) {
            return;
        }
        importIndonesianSample();
        importEnglishSample();
    }

    private void importIndonesianSample() throws IOException {
        ClassPathResource resource = new ClassPathResource(INDONESIAN_SAMPLE);
        if (!resource.exists()) {
            return;
        }
        List<ParsedReview> reviews = parser.parse(resource.getFilename(),
                resource.getContentAsString(StandardCharsets.UTF_8));
        String name = "Indonesian sample (" + reviews.size() + " reviews)";
        if (!batchRepository.existsByName(name)) {
            applyPrecomputed(importService.importReviews(name, reviews));
            log.info("Imported Indonesian sample with {} reviews", reviews.size());
        }
    }

    private void importEnglishSample() throws IOException {
        Path output = Path.of(coreNlpOutputFile);
        if (!Files.exists(output)) {
            log.warn("Sample data not loaded: {} not found", output.toAbsolutePath());
            return;
        }

        List<ParsedReview> reviews = parser.parse(output.toString(), Files.readString(output, StandardCharsets.UTF_8));

        Path labelsPath = Path.of(labelsFile);
        if (Files.exists(labelsPath)) {
            Map<Integer, ParsedReview> labelled = parser.parse(labelsPath.toString(),
                            Files.readString(labelsPath, StandardCharsets.UTF_8)).stream()
                    .collect(Collectors.toMap(ParsedReview::reviewId, Function.identity(), (a, b) -> a));
            reviews = reviews.stream()
                    .map(r -> {
                        ParsedReview raw = labelled.get(r.reviewId());
                        String label = raw == null ? null : raw.label();
                        return new ParsedReview(r.reviewId(), r.customer(), r.department(), r.date(),
                                r.comment(), label, r.coreNlpSentiment());
                    })
                    .toList();
        }

        String name = "Sample dataset (" + reviews.size() + " reviews)";
        if (!batchRepository.existsByName(name)) {
            applyPrecomputed(importService.importReviews(name, reviews));
            log.info("Imported sample dataset with {} reviews", reviews.size());
        }
    }

    /** Copies the stored Gemini results of a sample onto the freshly imported batch, if there are any. */
    private void applyPrecomputed(UploadBatch batch) throws IOException {
        ClassPathResource resource = new ClassPathResource(PRECOMPUTED_RESULTS);
        if (!precomputed || !resource.exists()) {
            return;
        }
        PrecomputedResults results = objectMapper.readValue(resource.getInputStream(), PrecomputedResults.class);
        PrecomputedResults.Batch stored = results.batches().stream()
                .filter(b -> b.name().equals(batch.getName()))
                .findFirst().orElse(null);
        if (stored == null) {
            return;
        }

        Map<Integer, PrecomputedResults.Review> byId = stored.results().stream()
                .collect(Collectors.toMap(PrecomputedResults.Review::reviewId, Function.identity(), (a, b) -> a));
        List<FeedbackRecord> records = recordRepository.findByBatchIdOrderByReviewId(batch.getId());
        int missing = 0;
        for (FeedbackRecord record : records) {
            PrecomputedResults.Review review = byId.get(record.getReviewId());
            if (review == null) {
                missing++;
                continue;
            }
            record.applyAnalysis(review.language(), review.aiSentiment(), review.category(), review.actionableInsight());
        }
        recordRepository.saveAll(records);

        // Only mark the batch analysed when every review has a result; otherwise Gemini fills the gaps as usual
        if (missing == 0) {
            batch.markAnalyzed(stored.aiCalls(), stored.processingMillis(), 0);
            if (stored.summary() != null && !stored.summary().isNull()) {
                batch.storeSummary(objectMapper.writeValueAsString(stored.summary()));
            }
            batchRepository.save(batch);
        }
        log.info("Applied precomputed Gemini results ({} on {}) to '{}'", results.generatedBy(), results.generatedOn(), batch.getName());
    }
}
