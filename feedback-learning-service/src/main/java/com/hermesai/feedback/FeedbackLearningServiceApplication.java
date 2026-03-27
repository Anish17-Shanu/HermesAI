package com.hermesai.feedback;

import com.hermesai.common.config.KafkaTopics;
import com.hermesai.common.events.EventSchemas;
import java.time.Instant;
import java.util.List;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@EnableKafka
@SpringBootApplication(scanBasePackages = "com.hermesai")
public class FeedbackLearningServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(FeedbackLearningServiceApplication.class, args);
    }
}

@RestController
@RequestMapping("/api/v1/feedback")
class FeedbackController {

    private final FeedbackLearningService service;

    FeedbackController(FeedbackLearningService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SYSTEM')")
    public ResponseEntity<List<FeedbackRecord>> list(@RequestParam(required = false) String decisionId) {
        return ResponseEntity.ok(decisionId == null ? service.all() : service.byDecision(decisionId));
    }
}

@org.springframework.stereotype.Service
class FeedbackLearningService {

    private final FeedbackRepository repository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    FeedbackLearningService(FeedbackRepository repository, KafkaTemplate<String, Object> kafkaTemplate) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(topics = KafkaTopics.REVIEW_COMPLETED, groupId = "feedback-learning-service")
    @Transactional
    public void onReviewCompleted(EventSchemas.ReviewCompletedEvent event) {
        FeedbackRecord record = new FeedbackRecord();
        record.setDecisionId(event.decisionId);
        record.setReviewId(event.reviewId);
        record.setCorrected(event.correctedAiDecision);
        record.setModelVersion("mock-model-v" + (1 + (repository.count() / 10)));
        record.setImprovementSignal(event.correctedAiDecision ? "SUPERVISED_CORRECTION" : "CONFIRMED_DECISION");
        record.setCreatedAt(Instant.now());
        repository.save(record);

        EventSchemas.FeedbackCapturedEvent feedbackCaptured = new EventSchemas.FeedbackCapturedEvent();
        feedbackCaptured.decisionId = record.getDecisionId();
        feedbackCaptured.reviewId = record.getReviewId();
        feedbackCaptured.modelVersion = record.getModelVersion();
        feedbackCaptured.corrected = record.isCorrected();
        feedbackCaptured.occurredAt = Instant.now();
        kafkaTemplate.send(KafkaTopics.FEEDBACK_CAPTURED, record.getDecisionId(), feedbackCaptured);
    }

    public List<FeedbackRecord> all() {
        return repository.findAll();
    }

    public List<FeedbackRecord> byDecision(String decisionId) {
        return repository.findByDecisionId(decisionId);
    }
}

@Entity
@Table(name = "feedback_records")
class FeedbackRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String decisionId;
    @Column(unique = true)
    private String reviewId;
    private String modelVersion;
    private boolean corrected;
    private String improvementSignal;
    private Instant createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getDecisionId() { return decisionId; }
    public void setDecisionId(String decisionId) { this.decisionId = decisionId; }
    public String getReviewId() { return reviewId; }
    public void setReviewId(String reviewId) { this.reviewId = reviewId; }
    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }
    public boolean isCorrected() { return corrected; }
    public void setCorrected(boolean corrected) { this.corrected = corrected; }
    public String getImprovementSignal() { return improvementSignal; }
    public void setImprovementSignal(String improvementSignal) { this.improvementSignal = improvementSignal; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

interface FeedbackRepository extends JpaRepository<FeedbackRecord, Long> {
    List<FeedbackRecord> findByDecisionId(String decisionId);
}
