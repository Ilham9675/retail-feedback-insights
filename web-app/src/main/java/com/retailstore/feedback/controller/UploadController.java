package com.retailstore.feedback.controller;

import com.retailstore.feedback.ingest.FeedbackImportService;
import com.retailstore.feedback.ingest.InvalidFeedbackFileException;
import com.retailstore.feedback.persistence.UploadBatch;
import com.retailstore.feedback.service.AnalysisJobService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Upload page for CSV and TXT feedback files.
 */
@Controller
public class UploadController {

    private final FeedbackImportService importService;
    private final AnalysisJobService analysisJobs;
    private final int maxReviews;

    public UploadController(FeedbackImportService importService,
                            AnalysisJobService analysisJobs,
                            @Value("${feedback.upload.max-reviews:200}") int maxReviews) {
        this.importService = importService;
        this.analysisJobs = analysisJobs;
        this.maxReviews = maxReviews;
    }

    @GetMapping("/upload")
    public String uploadForm(Model model) {
        model.addAttribute("maxReviews", maxReviews);
        return "upload";
    }

    @PostMapping("/upload")
    public String upload(@RequestParam("file") MultipartFile file, Model model) throws IOException {
        model.addAttribute("maxReviews", maxReviews);

        String name = file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename();
        String lower = name.toLowerCase(Locale.ROOT);
        if (file.isEmpty()) {
            model.addAttribute("error", "Please choose a file to upload.");
            return "upload";
        }
        if (!lower.endsWith(".csv") && !lower.endsWith(".txt")) {
            model.addAttribute("error", "Only .csv and .txt files are supported.");
            return "upload";
        }

        try {
            UploadBatch batch = importService.importFile(name, new String(file.getBytes(), StandardCharsets.UTF_8));
            // Start Gemini right away; the dashboard shows progress until it is done
            analysisJobs.startAnalysis(batch.getId());
            return "redirect:/?batch=" + batch.getId();
        } catch (InvalidFeedbackFileException e) {
            model.addAttribute("error", e.getMessage());
            return "upload";
        }
    }
}
