package com.retailstore.feedback.sentiment;

import edu.stanford.nlp.pipeline.CoreDocument;
import edu.stanford.nlp.pipeline.CoreSentence;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Analyzes the sentiment of customer feedback using Stanford CoreNLP.
 * This class reads feedback from a file, performs sentiment analysis,
 * and writes the results to an output file.
 */
public class SentimentAnalyzer {

    // Regular expressions to extract feedback components
    private static final Pattern FEEDBACK_NUMBER_PATTERN = Pattern.compile("Feedback #(\\d+).*");
    private static final Pattern CUSTOMER_PATTERN = Pattern.compile("Customer:\\s*(.+)");
    private static final Pattern DEPARTMENT_PATTERN = Pattern.compile("Department:\\s*(.+)");
    private static final Pattern DATE_PATTERN = Pattern.compile("Date:\\s*(.+)");
    private static final Pattern COMMENT_PATTERN = Pattern.compile("Comment:\\s*(.+)");

    // Stanford CoreNLP pipeline
    private final StanfordCoreNLP pipeline;

    /**
     * Constructor initializes the Stanford CoreNLP pipeline with necessary properties.
     */
    public SentimentAnalyzer() {
        // Set up pipeline properties
        Properties props = new Properties();
        // Set the list of annotators to run
        props.setProperty("annotators", "tokenize, ssplit, pos, lemma, parse, sentiment");
        // Build pipeline
        System.out.println("Initializing Stanford CoreNLP pipeline...");
        this.pipeline = new StanfordCoreNLP(props);
        System.out.println("Pipeline initialized successfully.");
    }

    /**
     * Main method to run the sentiment analysis.
     * Usage: SentimentAnalyzer [inputFile] [outputFile]
     */
    public static void main(String[] args) {
        SentimentAnalyzer analyzer = new SentimentAnalyzer();

        String inputFilePath = args.length > 0 ? args[0] : "data/store_feedback.txt";
        String outputFilePath = args.length > 1 ? args[1] : "data/sentiment_feedback_output.txt";

        try {
            System.out.println("Starting sentiment analysis...");
            System.out.println("Reading from: " + inputFilePath);

            // Process all feedback entries
            List<FeedbackEntry> feedbackEntries = analyzer.processFeedbackFile(inputFilePath);

            System.out.println("Processed " + feedbackEntries.size() + " feedback entries.");

            // Print first few entries for debugging
            if (!feedbackEntries.isEmpty()) {
                System.out.println("\nFirst entry processed:");
                FeedbackEntry first = feedbackEntries.get(0);
                System.out.println("ID: " + first.getId());
                System.out.println("Customer: " + first.getCustomer());
                System.out.println("Comment: " + first.getComment());
                System.out.println("Sentiment: " + first.getSentiment());
            }

            // Write results to output file
            analyzer.writeResults(feedbackEntries, outputFilePath);

            System.out.println("\nSentiment analysis completed successfully. Results written to " + outputFilePath);
        } catch (IOException e) {
            System.err.println("Error processing feedback: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Processes the feedback file and returns a list of feedback entries with sentiment analysis
     *
     * @param filePath Path to the feedback file
     * @return List of feedback entries with sentiment analysis
     * @throws IOException If an I/O error occurs
     */
    public List<FeedbackEntry> processFeedbackFile(String filePath) throws IOException {
        String content = Files.readString(Paths.get(filePath), StandardCharsets.UTF_8);

        List<FeedbackEntry> feedbackEntries = new ArrayList<>();

        // Split the content by the "Feedback #" pattern to get individual entries
        String[] rawEntries = content.split("(?=Feedback #)");

        System.out.println("Found " + (rawEntries.length - 1) + " potential feedback entries."); // -1 because first split might be empty

        for (int i = 0; i < rawEntries.length; i++) {
            String rawEntry = rawEntries[i].trim();

            // Skip empty entries
            if (rawEntry.isEmpty() || !rawEntry.startsWith("Feedback #")) {
                continue;
            }

            FeedbackEntry feedbackEntry = parseEntry(rawEntry);

            if (feedbackEntry != null) {
                // Analyze sentiment
                System.out.println("Analyzing sentiment for entry #" + feedbackEntry.getId());
                String sentiment = analyzeSentiment(feedbackEntry.comment);
                feedbackEntry.setSentiment(sentiment);

                feedbackEntries.add(feedbackEntry);
            } else {
                System.err.println("Failed to parse entry at position " + i);
            }
        }

        return feedbackEntries;
    }

    /**
     * Parses a single feedback entry text into a FeedbackEntry object
     *
     * @param entryText The text of a single feedback entry
     * @return FeedbackEntry object
     */
    private FeedbackEntry parseEntry(String entryText) {
        FeedbackEntry entry = new FeedbackEntry();

        try {
            // Split into lines for easier parsing
            String[] lines = entryText.split("\n");

            // Parse each line looking for specific patterns
            for (String line : lines) {
                line = line.trim();

                // Extract feedback number from first line
                if (line.startsWith("Feedback #")) {
                    Matcher numberMatcher = FEEDBACK_NUMBER_PATTERN.matcher(line);
                    if (numberMatcher.find()) {
                        entry.setId(Integer.parseInt(numberMatcher.group(1)));
                    }
                }

                // Extract customer info
                else if (line.startsWith("Customer:")) {
                    Matcher customerMatcher = CUSTOMER_PATTERN.matcher(line);
                    if (customerMatcher.find()) {
                        entry.setCustomer(customerMatcher.group(1).trim());
                    }
                }

                // Extract department
                else if (line.startsWith("Department:")) {
                    Matcher departmentMatcher = DEPARTMENT_PATTERN.matcher(line);
                    if (departmentMatcher.find()) {
                        entry.setDepartment(departmentMatcher.group(1).trim());
                    }
                }

                // Extract date
                else if (line.startsWith("Date:")) {
                    Matcher dateMatcher = DATE_PATTERN.matcher(line);
                    if (dateMatcher.find()) {
                        entry.setDate(dateMatcher.group(1).trim());
                    }
                }

                // Extract comment
                else if (line.startsWith("Comment:")) {
                    Matcher commentMatcher = COMMENT_PATTERN.matcher(line);
                    if (commentMatcher.find()) {
                        entry.setComment(commentMatcher.group(1).trim());
                    }
                }

                // Note: We're ignoring the original sentiment line as we'll calculate it ourselves
            }

            // Validate that we have all required fields
            if (entry.getId() > 0 && entry.getComment() != null) {
                return entry;
            } else {
                System.err.println("Invalid entry - missing ID or comment");
                System.err.println("ID: " + entry.getId());
                System.err.println("Comment: " + entry.getComment());
            }

        } catch (Exception e) {
            System.err.println("Error parsing entry: " + e.getMessage());
        }

        return null;
    }

    /**
     * Analyzes the sentiment of a comment using Stanford CoreNLP
     *
     * @param comment The comment to analyze
     * @return Sentiment (VERY_POSITIVE, POSITIVE, NEUTRAL, NEGATIVE, VERY_NEGATIVE)
     */
    public String analyzeSentiment(String comment) {
        // Create a document from the comment
        CoreDocument doc = new CoreDocument(comment);

        // Annotate the document
        pipeline.annotate(doc);

        // Get the sentiment scores for each sentence
        List<CoreSentence> sentences = doc.sentences();

        if (sentences.isEmpty()) {
            return "NEUTRAL";
        }

        // Calculate the average sentiment
        Map<String, Integer> sentimentCounts = new HashMap<>();
        for (CoreSentence sentence : sentences) {
            String sentiment = sentence.sentiment();
            sentimentCounts.put(sentiment, sentimentCounts.getOrDefault(sentiment, 0) + 1);
        }

        // Determine the most common sentiment
        return sentimentCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("NEUTRAL");
    }

    /**
     * Writes the results of sentiment analysis to an output file
     *
     * @param feedbackEntries List of feedback entries with sentiment analysis
     * @param outputFilePath Path to the output file
     * @throws IOException If an I/O error occurs
     */
    public void writeResults(List<FeedbackEntry> feedbackEntries, String outputFilePath) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(outputFilePath), StandardCharsets.UTF_8)) {
            // Write header
            writer.write("# Sentiment Analysis Results\n\n");

            // Write summary statistics
            writer.write("## Summary Statistics\n\n");

            // Count sentiments
            Map<String, Long> sentimentCounts = feedbackEntries.stream()
                    .collect(Collectors.groupingBy(FeedbackEntry::getSentiment, Collectors.counting()));

            writer.write("Total Feedback Entries: " + feedbackEntries.size() + "\n");
            writer.write("Sentiment Distribution:\n");

            for (Map.Entry<String, Long> entry : sentimentCounts.entrySet()) {
                double percentage = (double) entry.getValue() / feedbackEntries.size() * 100;
                writer.write(String.format(Locale.US, "- %s: %d (%.1f%%)\n", entry.getKey(), entry.getValue(), percentage));
            }

            writer.write("\n## Department Analysis\n\n");

            // Group by department
            Map<String, List<FeedbackEntry>> byDepartment = feedbackEntries.stream()
                    .collect(Collectors.groupingBy(FeedbackEntry::getDepartment));

            for (Map.Entry<String, List<FeedbackEntry>> entry : byDepartment.entrySet()) {
                writer.write("### " + entry.getKey() + "\n\n");

                // Count sentiments per department
                Map<String, Long> deptSentimentCounts = entry.getValue().stream()
                        .collect(Collectors.groupingBy(FeedbackEntry::getSentiment, Collectors.counting()));

                for (Map.Entry<String, Long> sentCount : deptSentimentCounts.entrySet()) {
                    double percentage = (double) sentCount.getValue() / entry.getValue().size() * 100;
                    writer.write(String.format(Locale.US, "- %s: %d (%.1f%%)\n", sentCount.getKey(), sentCount.getValue(), percentage));
                }

                writer.write("\n");
            }

            // Write detailed entries
            writer.write("## Detailed Feedback Entries\n\n");

            for (FeedbackEntry entry : feedbackEntries) {
                writer.write("Feedback #" + entry.getId() + "\n");
                writer.write("Customer: " + entry.getCustomer() + "\n");
                writer.write("Department: " + entry.getDepartment() + "\n");
                writer.write("Date: " + entry.getDate() + "\n");
                writer.write("Comment: " + entry.getComment() + "\n");
                writer.write("Sentiment: " + entry.getSentiment() + "\n\n");
            }
        }
    }

    /**
     * Inner class representing a feedback entry with sentiment analysis
     */
    public static class FeedbackEntry {
        private int id;
        private String customer;
        private String department;
        private String date;
        private String comment;
        private String sentiment;

        // Default values to avoid null
        public FeedbackEntry() {
            this.customer = "";
            this.department = "";
            this.date = "";
            this.comment = "";
            this.sentiment = "";
        }

        // Getters and setters
        public int getId() { return id; }
        public void setId(int id) { this.id = id; }

        public String getCustomer() { return customer; }
        public void setCustomer(String customer) { this.customer = customer; }

        public String getDepartment() { return department; }
        public void setDepartment(String department) { this.department = department; }

        public String getDate() { return date; }
        public void setDate(String date) { this.date = date; }

        public String getComment() { return comment; }
        public void setComment(String comment) { this.comment = comment; }

        public String getSentiment() { return sentiment; }
        public void setSentiment(String sentiment) { this.sentiment = sentiment; }
    }
}
