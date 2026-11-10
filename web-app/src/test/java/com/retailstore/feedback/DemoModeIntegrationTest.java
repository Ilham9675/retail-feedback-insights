package com.retailstore.feedback;

import com.retailstore.feedback.model.EvaluationReport;
import com.retailstore.feedback.persistence.UploadBatch;
import com.retailstore.feedback.service.EvaluationService;
import com.retailstore.feedback.service.ExecutiveSummaryService;
import com.retailstore.feedback.service.FeedbackService;
import com.retailstore.feedback.service.GeminiFeedbackAnalyzer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The public demo: samples arrive analysed from the stored results, and nothing can call Gemini.
 */
@SpringBootTest(properties = {"feedback.sample.precomputed=true", "feedback.demo.enabled=true"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DemoModeIntegrationTest {

    @MockitoBean
    private GeminiFeedbackAnalyzer analyzer;

    @Autowired
    private FeedbackService feedbackService;

    @Autowired
    private EvaluationService evaluationService;

    @Autowired
    private ExecutiveSummaryService summaryService;

    @Autowired
    private MockMvc mvc;

    @AfterEach
    void geminiIsNeverCalled() {
        verify(analyzer, never()).analyze(anyList());
        verify(analyzer, never()).summarize(anyList());
    }

    @Test
    void samplesArriveAnalysedWithStoredResultsAndSummaries() {
        List<UploadBatch> batches = feedbackService.listBatches();

        assertThat(batches).hasSize(2).allMatch(UploadBatch::isAnalyzed).allMatch(b -> b.getFailedReviews() == 0);
        batches.forEach(b -> assertThat(summaryService.find(b.getId())).isPresent());
        // The stored run reproduces the numbers in the README
        EvaluationReport english = evaluationService.evaluate(batches.get(0).getId());
        assertThat(english.models().get(1).correct()).isEqualTo(46);
        EvaluationReport indonesian = evaluationService.evaluate(batches.get(1).getId());
        assertThat(indonesian.models().get(0).correct()).isEqualTo(27);
        assertThat(feedbackService.getAnalyzedRecords(batches.get(1).getId())).allMatch(r -> "id".equals(r.getLanguage()));
    }

    @Test
    void pagesShowTheDemoBannerWithoutGeminiActions() throws Exception {
        Long id = feedbackService.getBatch(null).getId();

        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Live demo, read-only")))
                .andExpect(content().string(not(containsString("Upload reviews"))));
        mvc.perform(get("/summary").param("batch", id.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Priority actions")))
                .andExpect(content().string(not(containsString("Regenerate"))));
    }

    @Test
    void actionsThatWouldCallGeminiAreBlocked() throws Exception {
        Long id = feedbackService.getBatch(null).getId();
        MockMultipartFile file = new MockMultipartFile("file", "reviews.csv", "text/csv", "comment\nGreat\n".getBytes());

        mvc.perform(get("/upload")).andExpect(status().isForbidden())
                .andExpect(content().string(containsString("read-only demo")));
        mvc.perform(multipart("/upload").file(file)).andExpect(status().isForbidden());
        mvc.perform(post("/batches/{id}/summary", id)).andExpect(status().isForbidden());
        mvc.perform(post("/batches/{id}/reanalyze", id)).andExpect(status().isForbidden());
        assertThat(feedbackService.listBatches()).hasSize(2);
    }
}
