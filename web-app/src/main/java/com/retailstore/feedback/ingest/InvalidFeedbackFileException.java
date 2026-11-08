package com.retailstore.feedback.ingest;

/**
 * Thrown when an uploaded file cannot be turned into reviews; the message is shown to the user.
 */
public class InvalidFeedbackFileException extends RuntimeException {

    public InvalidFeedbackFileException(String message) {
        super(message);
    }
}
