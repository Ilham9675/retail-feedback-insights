package com.retailstore.feedback.controller;

import com.retailstore.feedback.model.EnhancedFeedback;
import com.retailstore.feedback.model.EvaluationReport;
import com.retailstore.feedback.persistence.UploadBatch;
import com.retailstore.feedback.service.AnalysisJobService;
import com.retailstore.feedback.service.EvaluationService;
import com.retailstore.feedback.service.FeedbackService;

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

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Dashboard, evaluation and JSON endpoints. Every page works on one batch, selected with {@code ?batch=id}
 * (default: the most recent batch). Batches without results show a progress page while Gemini runs in the background;
 * the JSON endpoints wait for the analysis instead.
 */
@Controller
public class FeedbackController {

    private final FeedbackService feedbackService;
    private final EvaluationService evaluationService;
    private final AnalysisJobService analysisJobs;

    public FeedbackController(FeedbackService feedbackService, EvaluationService evaluationService,
                              AnalysisJobService analysisJobs) {
        this.feedbackService = feedbackService;
        this.evaluationService = evaluationService;
        this.analysisJobs = analysisJobs;
    }

    @GetMapping("/")
    public String dashboard(@RequestParam(required = false) Long batch, Model model) {
        UploadBatch current = feedbackService.getBatch(batch);
        if (analysisJobs.isBusy(current)) {
            return progressPage(current, "dashboard", model);
        }
        model.addAttribute("summary", feedbackService.generateFeedbackSummary(current.getId()));
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

    /** Retries Gemini, in the background, for reviews of a batch that have no result yet. */
    @PostMapping("/batches/{id}/reanalyze")
    public String reanalyze(@PathVariable Long id) {
        analysisJobs.startRetry(id);
        return "redirect:/?batch=" + id;
    }

    /** Analysis progress, polled by the progress page. */
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

    @GetMapping("/getfeedback")
    @ResponseBody
    public List<EnhancedFeedback> getFeedback(@RequestParam(required = false) Long batch) {
        return feedbackService.getEnhancedFeedback(batch);
    }

    @GetMapping("/api/evaluation")
    @ResponseBody
    public EvaluationReport evaluationJson(@RequestParam(required = false) Long batch) {
        return evaluationService.evaluate(feedbackService.getBatch(batch).getId());
    }

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
