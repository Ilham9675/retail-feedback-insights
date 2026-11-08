package com.retailstore.feedback.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Files over the multipart size limit are rejected before the controller runs; show the upload page with a message.
 */
@ControllerAdvice
public class UploadErrorAdvice {

    private final int maxReviews;
    private final String maxFileSize;

    public UploadErrorAdvice(@Value("${feedback.upload.max-reviews:200}") int maxReviews,
                             @Value("${spring.servlet.multipart.max-file-size:1MB}") String maxFileSize) {
        this.maxReviews = maxReviews;
        this.maxFileSize = maxFileSize;
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String tooLarge(Model model) {
        model.addAttribute("maxReviews", maxReviews);
        model.addAttribute("error", "The file is larger than " + maxFileSize + ".");
        return "upload";
    }
}
