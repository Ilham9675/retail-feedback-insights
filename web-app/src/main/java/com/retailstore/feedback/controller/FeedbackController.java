package com.retailstore.feedback.controller;

import com.retailstore.feedback.model.EnhancedFeedback;
import com.retailstore.feedback.model.EvaluationReport;
import com.retailstore.feedback.model.ExecutiveSummary;
import com.retailstore.feedback.model.FeedbackFilter;
import com.retailstore.feedback.persistence.UploadBatch;
import com.retailstore.feedback.service.AnalysisJobService;
import com.retailstore.feedback.service.EvaluationService;
import com.retailstore.feedback.service.ExecutiveSummaryService;
import com.retailstore.feedback.service.FeedbackService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Dashboard, evaluation and JSON endpoints. Every page works on one batch, selected with {@code ?batch=id}
 * (default: the most recent batch). Batches without results show a progress page while Gemini runs in the background;
 * the JSON endpoints wait for the analysis instead.
 */
@Controller
@Tag(name = "Feedback", description = "Analysed reviews, evaluation, executive summaries and analysis status")
public class FeedbackController {

    private final FeedbackService feedbackService;
    private final EvaluationService evaluationService;
    private final AnalysisJobService analysisJobs;
    private final ExecutiveSummaryService summaryService;

    public FeedbackController(FeedbackService feedbackService, EvaluationService evaluationService,
                              AnalysisJobService analysisJobs, ExecutiveSummaryService summaryService) {
        this.feedbackService = feedbackService;
        this.evaluationService = evaluationService;
        this.analysisJobs = analysisJobs;
        this.summaryService = summaryService;
    }

    @GetMapping("/")
    public String dashboard(@RequestParam(required = false) Long batch,
                            @RequestParam(required = false) String department,
                            @RequestParam(required = false) String sentiment,
                            @RequestParam(required = false) String category,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                            Model model) {
        UploadBatch current = feedbackService.getBatch(batch);
        if (analysisJobs.isBusy(current)) {
            return progressPage(current, "dashboard", model);
        }
        FeedbackFilter filter = FeedbackFilter.of(department, sentiment, category, from, to);
        model.addAttribute("summary", feedbackService.generateFeedbackSummary(current.getId(), filter));
        model.addAttribute("filter", filter);
        addBatchSelector(model, feedbackService.getBatch(current.getId()));
        return "dashboard";
    }

    @GetMapping("/evaluation")
    public String evaluation(@RequestParam(required = false) Long batch, Model model) {
        UploadBatch current = feedbackService.getBatch(batch);
        if (analysisJobs.isBusy(current)) {
            return progressPage(current, "evaluation", model);
        }
        model.addAttribute("report", evaluationService.evaluate(current.getId()));
        addBatchSelector(model, feedbackService.getBatch(current.getId()));
        return "evaluation";
    }

    /** Executive summary page; the summary itself is only generated when the button is pressed. */
    @GetMapping("/summary")
    public String summary(@RequestParam(required = false) Long batch, Model model) {
        UploadBatch current = feedbackService.getBatch(batch);
        if (analysisJobs.isBusy(current)) {
            return progressPage(current, "summary", model);
        }
        ExecutiveSummary executiveSummary = summaryService.find(current.getId()).orElse(null);
        Map<String, ExecutiveSummaryService.DepartmentStats> stats = summaryService.departmentStats(current.getId());
        model.addAttribute("executiveSummary", executiveSummary);
        model.addAttribute("stats", stats);
        if (executiveSummary != null) {
            model.addAttribute("departments", ExecutiveSummaryService.orderByStats(executiveSummary, stats));
        }
        current = feedbackService.getBatch(current.getId());
        if (current.getSummaryGeneratedAt() != null) {
            model.addAttribute("generatedAt", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                    .withZone(ZoneId.systemDefault()).format(current.getSummaryGeneratedAt()));
        }
        addBatchSelector(model, current);
        return "summary";
    }

    /** Generates (or regenerates) the executive summary with one Gemini request. */
    @PostMapping("/batches/{id}/summary")
    public String generateSummary(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            summaryService.generate(id);
        } catch (NoSuchElementException e) {
            throw e;
        } catch (Exception e) {
            redirect.addFlashAttribute("summaryError", "The summary could not be generated: " + e.getMessage());
        }
        return "redirect:/summary?batch=" + id;
    }

    /** Stored executive summary as JSON; 404 when none was generated yet (this endpoint never calls Gemini). */
    @Operation(summary = "Stored executive summary of a batch",
            description = "Overview plus main issues and priority actions per department. 404 when none was generated; never calls Gemini.")
    @GetMapping("/api/batches/{id}/summary")
    @ResponseBody
    public ResponseEntity<ExecutiveSummary> summaryJson(@PathVariable Long id) {
        return ResponseEntity.of(summaryService.find(id));
    }

    /** Retries Gemini, in the background, for reviews of a batch that have no result yet. */
    @PostMapping("/batches/{id}/reanalyze")
    public String reanalyze(@PathVariable Long id) {
        analysisJobs.startRetry(id);
        return "redirect:/?batch=" + id;
    }

    /** Analysis progress, polled by the progress page. */
    @Operation(summary = "Analysis progress of a batch", description = "Polled by the progress page while Gemini runs in the background.")
    @GetMapping("/api/batches/{id}/status")
    @ResponseBody
    public Map<String, Object> status(@PathVariable Long id) {
        return analysisJobs.status(id);
    }

    // Starts the background analysis if needed and shows a page that reloads when it is done
    private String progressPage(UploadBatch batch, String page, Model model) {
        analysisJobs.startAnalysis(batch.getId());
        model.addAttribute("page", page);
        addBatchSelector(model, batch);
        return "analyzing";
    }

    @Operation(summary = "Analysed reviews of a batch",
            description = "CoreNLP sentiment plus Gemini sentiment, category and actionable insight. All parameters are optional; "
                    + "batch defaults to the most recent one and dates are YYYY-MM-DD. Waits for the analysis if the batch is not analysed yet.")
    @GetMapping("/getfeedback")
    @ResponseBody
    public List<EnhancedFeedback> getFeedback(@RequestParam(required = false) Long batch,
                                              @RequestParam(required = false) String department,
                                              @RequestParam(required = false) String sentiment,
                                              @RequestParam(required = false) String category,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return feedbackService.getEnhancedFeedback(batch, FeedbackFilter.of(department, sentiment, category, from, to));
    }

    @Operation(summary = "Sentiment accuracy of CoreNLP and Gemini against the dataset labels")
    @GetMapping("/api/evaluation")
    @ResponseBody
    public EvaluationReport evaluationJson(@RequestParam(required = false) Long batch) {
        return evaluationService.evaluate(feedbackService.getBatch(batch).getId());
    }

    @Operation(summary = "All batches with review counts and analysis status")
    @GetMapping("/api/batches")
    @ResponseBody
    public List<UploadBatch> batches() {
        return feedbackService.listBatches();
    }

    private void addBatchSelector(Model model, UploadBatch current) {
        model.addAttribute("currentBatch", current);
        model.addAttribute("batches", feedbackService.listBatches());
    }

    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String notFound(NoSuchElementException e, Model model) {
        model.addAttribute("error", e.getMessage());
        return "error";
    }
}
