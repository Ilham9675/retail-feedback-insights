package com.retailstore.feedback.ingest;

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
 */
@Component
public class SampleDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SampleDataLoader.class);

    private final FeedbackFileParser parser;
    private final FeedbackImportService importService;
    private final UploadBatchRepository batchRepository;
    private final boolean enabled;
    private final String coreNlpOutputFile;
    private final String labelsFile;

    public SampleDataLoader(FeedbackFileParser parser,
                            FeedbackImportService importService,
                            UploadBatchRepository batchRepository,
                            @Value("${feedback.sample.enabled:true}") boolean enabled,
                            @Value("${feedback.input-file:data/sentiment_feedback_output.txt}") String coreNlpOutputFile,
                            @Value("${feedback.labels-file:data/store_feedback.txt}") String labelsFile) {
        this.parser = parser;
        this.importService = importService;
        this.batchRepository = batchRepository;
        this.enabled = enabled;
        this.coreNlpOutputFile = coreNlpOutputFile;
        this.labelsFile = labelsFile;
    }

    static final String INDONESIAN_SAMPLE = "static/samples/sample_reviews_id.csv";

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
            importService.importReviews(name, reviews);
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
            importService.importReviews(name, reviews);
            log.info("Imported sample dataset with {} reviews", reviews.size());
        }
    }
}
