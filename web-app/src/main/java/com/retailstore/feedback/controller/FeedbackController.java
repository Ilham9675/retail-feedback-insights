package com.retailstore.feedback.controller;

import com.retailstore.feedback.model.EnhancedFeedback;
import com.retailstore.feedback.model.EvaluationReport;
import com.retailstore.feedback.model.FeedbackSummary;
import com.retailstore.feedback.service.EvaluationService;
import com.retailstore.feedback.service.FeedbackService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.util.List;

/**
 * Controller for feedback-related endpoints.
 */
@Controller
public class FeedbackController {

    @Autowired
    private FeedbackService feedbackService;

    @Autowired
    private EvaluationService evaluationService;

    /**
     * Displays the dashboard page.
     *
     * @param model Model to add attributes to
     * @return The name of the view to render
     */
    @GetMapping("/")
    public String dashboard(Model model) {
        try {
            FeedbackSummary summary = feedbackService.generateFeedbackSummary();
            model.addAttribute("summary", summary);
            return "dashboard";
        } catch (IOException e) {
            model.addAttribute("error", "Error loading feedback data: " + e.getMessage());
            return "error";
        }
    }

    /**
     * Returns all feedback data in JSON format.
     *
     * @return List of enhanced feedback entries
     */
    @GetMapping("/getfeedback")
    @ResponseBody
    public ResponseEntity<List<EnhancedFeedback>> getFeedback() {
        try {
            List<EnhancedFeedback> feedback = feedbackService.getEnhancedFeedback();
            return ResponseEntity.ok(feedback);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Displays sentiment accuracy of Stanford CoreNLP and Gemini against the dataset labels.
     */
    @GetMapping("/evaluation")
    public String evaluation(Model model) {
        try {
            model.addAttribute("report", evaluationService.evaluate());
            return "evaluation";
        } catch (IOException e) {
            model.addAttribute("error", "Error loading evaluation data: " + e.getMessage());
            return "error";
        }
    }

    /**
     * Returns the same evaluation as JSON.
     */
    @GetMapping("/api/evaluation")
    @ResponseBody
    public ResponseEntity<EvaluationReport> evaluationJson() {
        try {
            return ResponseEntity.ok(evaluationService.evaluate());
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
