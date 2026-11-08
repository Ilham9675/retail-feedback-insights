package com.retailstore.feedback.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UploadBatchRepository extends JpaRepository<UploadBatch, Long> {

    List<UploadBatch> findAllByOrderByCreatedAtDescIdDesc();

    Optional<UploadBatch> findFirstByOrderByCreatedAtDescIdDesc();

    boolean existsByName(String name);
}
