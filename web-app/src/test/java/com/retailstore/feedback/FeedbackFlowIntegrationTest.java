package com.retailstore.feedback;

import com.retailstore.feedback.ingest.SampleDataLoader;
import com.retailstore.feedback.model.AiReview;
import com.retailstore.feedback.model.EvaluationReport;
import com.retailstore.feedback.model.FeedbackEntry;
import com.retailstore.feedback.persistence.FeedbackRecord;
import com.retailstore.feedback.persistence.UploadBatch;
import com.retailstore.feedback.persistence.UploadBatchRepository;
import com.retailstore.feedback.service.EvaluationService;
import com.retailstore.feedback.service.FeedbackService;
import com.retailstore.feedback.service.GeminiFeedbackAnalyzer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full flow with the real database and controllers; only Gemini is mocked.
 * Each test gets a fresh context so the sample data starts unanalysed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class FeedbackFlowIntegrationTest {

    @MockitoBean
    private GeminiFeedbackAnalyzer analyzer;

    @Autowired
    private FeedbackService feedbackService;

    @Autowired
    private EvaluationService evaluationService;

    @Autowired
    private UploadBatchRepository batchRepository;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private SampleDataLoader sampleDataLoader;

    @BeforeEach
    void geminiCallsEverythingNegative() {
        doAnswer(invocation -> {
            List<FeedbackEntry> batch = invocation.getArgument(0);
            return batch.stream()
                    .map(e -> new AiReview(e.getId(), "en", "negative", "Store Experience", "Fix it"))
                    .toList();
        }).when(analyzer).analyze(anyList());
    }

    @Test
    void bothSamplesAreImportedOnStartupAndEnglishIsTheDefault() {
        List<UploadBatch> batches = feedbackService.listBatches();
        assertThat(batches).extracting(UploadBatch::getName)
                .containsExactly("Sample dataset (50 reviews)", "Indonesian sample (30 reviews)");
        assertThat(batches).noneMatch(UploadBatch::isAnalyzed);
        assertThat(feedbackService.getBatch(null).getReviewCount()).isEqualTo(50);
    }

    @Test
    void sampleImportIsIdempotent() throws Exception {
        sampleDataLoader.run(null);

        assertThat(batchRepository.count()).isEqualTo(2);
    }

    @Test
    void indonesianSampleHasLabelsAndKeepsTheDetectedLanguage() {
        UploadBatch indonesian = feedbackService.listBatches().get(1);
        doAnswer(invocation -> {
            List<FeedbackEntry> batch = invocation.getArgument(0);
            return batch.stream()
                    .map(e -> new AiReview(e.getId(), " ID ", "positive", "Customer Service", "Pertahankan pelayanan"))
                    .toList();
        }).when(analyzer).analyze(anyList());

        List<FeedbackRecord> records = feedbackService.getAnalyzedRecords(indonesian.getId());

        assertThat(records).hasSize(30).allMatch(r -> r.getLabel() != null && r.getCoreNlpSentiment() == null);
        assertThat(records).allMatch(r -> "id".equals(r.getLanguage()));
        // CoreNLP can't analyse Indonesian, so only Gemini is scored; the sample has 12 positive labels
        EvaluationReport report = evaluationService.evaluate(indonesian.getId());
        assertThat(report.models()).extracting(EvaluationReport.ModelScore::model).containsExactly("Gemini");
        assertThat(report.models().get(0).correct()).isEqualTo(12);
    }

    @Test
    void batchIsAnalyzedOnceInChunksAndResultsArePersisted() {
        List<FeedbackRecord> records = feedbackService.getAnalyzedRecords(null);

        verify(analyzer, times(2)).analyze(anyList());
        assertThat(records).hasSize(50).allMatch(r -> "NEGATIVE".equals(r.getAiSentiment()));

        // Second read comes from the database, no new Gemini calls
        feedbackService.getAnalyzedRecords(null);
        verify(analyzer, times(2)).analyze(anyList());
        assertThat(feedbackService.getBatch(null).getAiCalls()).isEqualTo(2);
    }

    @Test
    void failedRequestsAreCountedAndCanBeRetried() {
        doThrow(new RuntimeException("quota exceeded")).when(analyzer).analyze(anyList());

        List<FeedbackRecord> records = feedbackService.getAnalyzedRecords(null);

        assertThat(records).allMatch(r -> r.getCategory().equals("Uncategorized"))
                .allMatch(r -> r.getActionableInsight().contains("quota exceeded"));
        assertThat(feedbackService.getBatch(null).getFailedReviews()).isEqualTo(50);

        geminiCallsEverythingNegative();
        feedbackService.reanalyzeFailed(null);

        assertThat(feedbackService.getBatch(null).getFailedReviews()).isZero();
    }

    @Test
    void evaluationScoresBothModelsOnTheSample() {
        EvaluationReport report = evaluationService.evaluate(null);

        assertThat(report.totalLabelled()).isEqualTo(50);
        assertThat(report.models()).extracting(EvaluationReport.ModelScore::model)
                .containsExactly("Stanford CoreNLP", "Gemini");
        // The mock answers NEGATIVE for everything; the sample has 15 negative labels
        assertThat(report.models().get(1).correct()).isEqualTo(15);
        assertThat(report.models().get(0).correct()).isEqualTo(23);
    }

    @Test
    void uploadedCsvBecomesTheCurrentBatch() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "reviews.csv", "text/csv",
                "comment,label\nGreat staff,POSITIVE\nLong queue,NEGATIVE\n".getBytes());

        mvc.perform(multipart("/upload").file(file))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/?batch=*"));

        UploadBatch latest = feedbackService.getBatch(null);
        assertThat(latest.getName()).isEqualTo("reviews.csv");
        assertThat(latest.getReviewCount()).isEqualTo(2);

        mvc.perform(get("/").param("batch", latest.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("reviews.csv")));
        // Only the two uploaded reviews were sent, not the sample
        verify(analyzer, times(1)).analyze(anyList());
    }

    @Test
    void invalidUploadShowsAnErrorAndStoresNothing() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "reviews.csv", "text/csv",
                "text\nno comment column\n".getBytes());

        mvc.perform(multipart("/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("comment")));

        assertThat(batchRepository.count()).isEqualTo(2);
        verify(analyzer, never()).analyze(anyList());
    }

    @Test
    void unknownBatchReturns404() throws Exception {
        mvc.perform(get("/").param("batch", "999")).andExpect(status().isNotFound());
    }
}
