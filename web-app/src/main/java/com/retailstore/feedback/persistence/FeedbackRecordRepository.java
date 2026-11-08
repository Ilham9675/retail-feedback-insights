package com.retailstore.feedback.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FeedbackRecordRepository extends JpaRepository<FeedbackRecord, Long> {

    List<FeedbackRecord> findByBatchIdOrderByReviewId(Long batchId);
}
