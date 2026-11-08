package com.retailstore.feedback.ingest;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns feedback files into {@link ParsedReview}s. Supported formats:
 * <ul>
 *   <li>CSV with a header row: {@code comment} is required; {@code id, customer, department, date, label} are optional</li>
 *   <li>The raw text dataset: blocks starting with {@code Feedback #12 [NEGATIVE]}</li>
 *   <li>The sentiment-core output: the same blocks after {@code ## Detailed Feedback Entries}, with CoreNLP sentiment</li>
 * </ul>
 */
@Component
public class FeedbackFileParser {

    private static final Pattern HEADER_PATTERN = Pattern.compile("Feedback #(\\d+)(?:\\s*\\[(\\w+)])?");
    private static final Pattern CUSTOMER_PATTERN = Pattern.compile("Customer:\\s*(.+)");
    private static final Pattern DEPARTMENT_PATTERN = Pattern.compile("Department:\\s*(.+)");
    private static final Pattern DATE_PATTERN = Pattern.compile("Date:\\s*(.+)");
    private static final Pattern COMMENT_PATTERN = Pattern.compile("Comment:\\s*(.+)");
    private static final Pattern SENTIMENT_PATTERN = Pattern.compile("Sentiment:\\s*(.+)");

    private static final String CORENLP_SECTION = "## Detailed Feedback Entries";
    private static final List<String> LABELS = List.of("POSITIVE", "NEUTRAL", "NEGATIVE");

    public List<ParsedReview> parse(String fileName, String content) {
        if (content == null || content.isBlank()) {
            throw new InvalidFeedbackFileException("The file is empty.");
        }
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        List<ParsedReview> reviews = name.endsWith(".csv") ? parseCsv(content) : parseText(content);
        if (reviews.isEmpty()) {
            throw new InvalidFeedbackFileException(name.endsWith(".csv")
                    ? "No reviews found. Each row needs a non-empty \"comment\" value."
                    : "No reviews found. Each review must start with a line like \"Feedback #1\" and contain a \"Comment:\" line.");
        }
        return reviews;
    }

    private List<ParsedReview> parseCsv(String content) {
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreHeaderCase(true)
                .setTrim(true)
                .setIgnoreEmptyLines(true)
                .get();

        try (CSVParser parser = CSVParser.parse(new StringReader(stripBom(content)), format)) {
            Map<String, Integer> header = parser.getHeaderMap();
            if (header == null || header.keySet().stream().noneMatch(h -> h.equalsIgnoreCase("comment"))) {
                throw new InvalidFeedbackFileException("The CSV header must include a \"comment\" column.");
            }

            List<ParsedReview> reviews = new ArrayList<>();
            int row = 0;
            for (CSVRecord record : parser) {
                row++;
                String comment = value(record, "comment");
                if (comment == null) {
                    continue;
                }
                String id = value(record, "id");
                reviews.add(new ParsedReview(
                        id == null ? row : parseId(id, row),
                        value(record, "customer"),
                        value(record, "department"),
                        value(record, "date"),
                        comment,
                        normalizeLabel(value(record, "label")),
                        null));
            }
            return reviews;
        } catch (IOException | IllegalArgumentException | IllegalStateException e) {
            throw new InvalidFeedbackFileException("The CSV file could not be read: " + e.getMessage());
        }
    }

    private List<ParsedReview> parseText(String content) {
        String text = stripBom(content);
        boolean coreNlpOutput = text.contains(CORENLP_SECTION);
        if (coreNlpOutput) {
            text = text.substring(text.indexOf(CORENLP_SECTION) + CORENLP_SECTION.length());
        }

        List<ParsedReview> reviews = new ArrayList<>();
        for (String block : text.split("(?=Feedback #)")) {
            Matcher header = HEADER_PATTERN.matcher(block);
            String comment = find(COMMENT_PATTERN, block);
            if (!block.startsWith("Feedback #") || !header.find() || comment == null) {
                continue;
            }
            String sentiment = find(SENTIMENT_PATTERN, block);
            // In the raw dataset the [LABEL] in the header is the ground truth;
            // in the sentiment-core output the Sentiment line is CoreNLP's prediction
            String label = normalizeLabel(header.group(2));
            String coreNlp = coreNlpOutput && sentiment != null
                    ? sentiment.trim().toUpperCase(Locale.ROOT).replace(' ', '_')
                    : null;

            reviews.add(new ParsedReview(
                    Integer.parseInt(header.group(1)),
                    find(CUSTOMER_PATTERN, block),
                    find(DEPARTMENT_PATTERN, block),
                    find(DATE_PATTERN, block),
                    comment,
                    label,
                    coreNlp));
        }
        return reviews;
    }

    private static String value(CSVRecord record, String column) {
        if (!record.isMapped(column) || !record.isSet(column)) {
            return null;
        }
        String v = record.get(column);
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static int parseId(String id, int row) {
        try {
            return Integer.parseInt(id);
        } catch (NumberFormatException e) {
            throw new InvalidFeedbackFileException("Row " + row + ": \"id\" must be a whole number, got \"" + id + "\".");
        }
    }

    /** Keeps POSITIVE / NEUTRAL / NEGATIVE labels (any case); anything else is treated as no label. */
    static String normalizeLabel(String label) {
        if (label == null) {
            return null;
        }
        String l = label.trim().toUpperCase(Locale.ROOT);
        return LABELS.contains(l) ? l : null;
    }

    private static String find(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group(1).trim() : null;
    }

    private static String stripBom(String text) {
        return text.startsWith("﻿") ? text.substring(1) : text;
    }
}
