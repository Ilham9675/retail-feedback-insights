package com.retailstore.feedback.ingest;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FeedbackFileParserTest {

    private final FeedbackFileParser parser = new FeedbackFileParser();

    @Test
    void parsesCsvWithAllColumns() {
        String csv = """
                id,customer,department,date,comment,label
                7,Jane,Checkout,2025-05-01,"Fast, friendly checkout",positive
                8,Ahmad,Grocery,2025-05-02,Expired milk,NEGATIVE
                """;

        List<ParsedReview> reviews = parser.parse("reviews.csv", csv);

        assertThat(reviews).hasSize(2);
        assertThat(reviews.get(0)).isEqualTo(new ParsedReview(7, "Jane", "Checkout", "2025-05-01",
                "Fast, friendly checkout", "POSITIVE", null));
        assertThat(reviews.get(1).label()).isEqualTo("NEGATIVE");
    }

    @Test
    void csvNeedsOnlyACommentColumnAndNumbersRowsWithoutIds() {
        String csv = "﻿Comment\nGreat store\n\nToo crowded\n";

        List<ParsedReview> reviews = parser.parse("REVIEWS.CSV", csv);

        assertThat(reviews).extracting(ParsedReview::reviewId).containsExactly(1, 2);
        assertThat(reviews).extracting(ParsedReview::label).containsOnlyNulls();
    }

    @Test
    void unknownLabelsAreIgnored() {
        List<ParsedReview> reviews = parser.parse("r.csv", "comment,label\nOk,MIXED\n");

        assertThat(reviews.get(0).label()).isNull();
    }

    @Test
    void rejectsCsvWithoutCommentColumn() {
        assertThatThrownBy(() -> parser.parse("r.csv", "text,label\nhello,POSITIVE\n"))
                .isInstanceOf(InvalidFeedbackFileException.class)
                .hasMessageContaining("comment");
    }

    @Test
    void rejectsNonNumericId() {
        assertThatThrownBy(() -> parser.parse("r.csv", "id,comment\nabc,hello\n"))
                .isInstanceOf(InvalidFeedbackFileException.class)
                .hasMessageContaining("Row 1");
    }

    @Test
    void rejectsEmptyFile() {
        assertThatThrownBy(() -> parser.parse("r.txt", "  "))
                .isInstanceOf(InvalidFeedbackFileException.class);
    }

    @Test
    void readsLabelsFromRawTextDataset() throws Exception {
        List<ParsedReview> reviews = parser.parse("store_feedback.txt", read("../data/store_feedback.txt"));

        assertThat(reviews).hasSize(50);
        assertThat(reviews.get(0).label()).isEqualTo("POSITIVE");
        assertThat(reviews.get(1).label()).isEqualTo("NEGATIVE");
        assertThat(reviews).extracting(ParsedReview::coreNlpSentiment).containsOnlyNulls();
    }

    @Test
    void readsCoreNlpSentimentFromSentimentCoreOutput() throws Exception {
        List<ParsedReview> reviews = parser.parse("output.txt", read("../data/sentiment_feedback_output.txt"));

        assertThat(reviews).hasSize(50);
        assertThat(reviews.get(0).coreNlpSentiment()).isEqualTo("POSITIVE");
        assertThat(reviews.get(1).coreNlpSentiment()).isEqualTo("NEUTRAL");
        assertThat(reviews.get(0).department()).isEqualTo("Returns");
        assertThat(reviews).extracting(ParsedReview::label).containsOnlyNulls();
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
