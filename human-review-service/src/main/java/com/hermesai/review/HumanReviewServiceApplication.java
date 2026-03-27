package com.hermesai.review;

import com.hermesai.common.api.ApiContracts;
import com.hermesai.common.config.KafkaTopics;
import com.hermesai.common.events.EventSchemas;
import com.hermesai.common.model.DomainTypes;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@EnableKafka
@SpringBootApplication(scanBasePackages = "com.hermesai")
public class HumanReviewServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(HumanReviewServiceApplication.class, args);
    }
}

@RestController
@RequestMapping("/api/v1/reviews")
class HumanReviewController {

    private final HumanReviewApplicationService service;

    HumanReviewController(HumanReviewApplicationService service) {
        this.service = service;
    }

    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('ADMIN','REVIEWER')")
    public List<ReviewTask> pending() {
        return service.pendingReviews();
    }

    @PostMapping("/{reviewId}/approve")
    @PreAuthorize("hasAnyRole('ADMIN','REVIEWER')")
    public ResponseEntity<ReviewTask> approve(@PathVariable String reviewId,
                                              @RequestBody ApiContracts.ReviewActionRequest request) {
        return ResponseEntity.ok(service.complete(reviewId, request, DomainTypes.ReviewAction.APPROVE));
    }

    @PostMapping("/{reviewId}/reject")
    @PreAuthorize("hasAnyRole('ADMIN','REVIEWER')")
    public ResponseEntity<ReviewTask> reject(@PathVariable String reviewId,
                                             @RequestBody ApiContracts.ReviewActionRequest request) {
        return ResponseEntity.ok(service.complete(reviewId, request, DomainTypes.ReviewAction.REJECT));
    }

    @PostMapping("/{reviewId}/override")
    @PreAuthorize("hasAnyRole('ADMIN','REVIEWER')")
    public ResponseEntity<ReviewTask> override(@PathVariable String reviewId,
                                               @RequestBody ApiContracts.ReviewActionRequest request) {
        return ResponseEntity.ok(service.complete(reviewId, request, DomainTypes.ReviewAction.OVERRIDE));
    }
}

@org.springframework.stereotype.Service
class HumanReviewApplicationService {

    private final ReviewTaskRepository repository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final StringRedisTemplate redisTemplate;
    private final MeterRegistry meterRegistry;

    HumanReviewApplicationService(ReviewTaskRepository repository,
                                  KafkaTemplate<String, Object> kafkaTemplate,
                                  StringRedisTemplate redisTemplate,
                                  MeterRegistry meterRegistry) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.redisTemplate = redisTemplate;
        this.meterRegistry = meterRegistry;
    }

    @KafkaListener(topics = KafkaTopics.REVIEW_REQUESTED, groupId = "human-review-service")
    @Transactional
    public void onReviewRequested(EventSchemas.ReviewRequestedEvent event) {
        if (repository.findByReviewId(event.reviewId).isPresent()) {
            return;
        }
        ReviewTask task = new ReviewTask();
        task.setReviewId(event.reviewId);
        task.setDecisionId(event.decisionId);
        task.setAssignedReviewer(event.assignedReviewer);
        task.setPriority(event.priority);
        task.setStatus("PENDING");
        task.setDueAt(event.dueAt);
        task.setCreatedAt(Instant.now());
        repository.save(task);

        double queueScore = "URGENT".equalsIgnoreCase(event.priority) ? 100.0 : 10.0;
        redisTemplate.opsForZSet().add("reviews:priority", event.reviewId, queueScore);
    }

    public List<ReviewTask> pendingReviews() {
        return repository.findByStatusOrderByDueAtAsc("PENDING");
    }

    @Transactional
    public ReviewTask complete(String reviewId,
                               ApiContracts.ReviewActionRequest request,
                               DomainTypes.ReviewAction action) {
        Timer.Sample timer = Timer.start(meterRegistry);
        try {
            ReviewTask task = repository.findByReviewId(reviewId)
                    .orElseThrow(() -> new IllegalArgumentException("Review not found"));
            task.setStatus(action.name());
            task.setReviewerId(request.reviewerId);
            task.setComments(request.comments);
            String finalDecision = action == DomainTypes.ReviewAction.OVERRIDE && request.overrideDecision != null
                    ? request.overrideDecision
                    : action == DomainTypes.ReviewAction.REJECT ? "REJECT" : "APPROVE";
            task.setFinalDecision(finalDecision);
            task.setCompletedAt(Instant.now());
            repository.save(task);
            redisTemplate.opsForZSet().remove("reviews:priority", reviewId);

            EventSchemas.ReviewCompletedEvent event = new EventSchemas.ReviewCompletedEvent();
            event.decisionId = task.getDecisionId();
            event.reviewId = task.getReviewId();
            event.reviewerId = request.reviewerId;
            event.finalDecision = finalDecision;
            event.action = action.name();
            event.comments = request.comments;
            event.correctedAiDecision = action == DomainTypes.ReviewAction.OVERRIDE;
            event.occurredAt = Instant.now();
            kafkaTemplate.send(KafkaTopics.REVIEW_COMPLETED, task.getDecisionId(), event);

            EventSchemas.AuditEvent audit = new EventSchemas.AuditEvent();
            audit.auditId = UUID.randomUUID().toString();
            audit.decisionId = task.getDecisionId();
            audit.actor = request.reviewerId;
            audit.eventType = "HUMAN_REVIEW_" + action.name();
            audit.payload = request.comments;
            audit.occurredAt = Instant.now();
            kafkaTemplate.send(KafkaTopics.AUDIT_EVENTS, task.getDecisionId(), audit);

            meterRegistry.counter("hermes.review.completed", "action", action.name()).increment();
            return task;
        } finally {
            timer.stop(meterRegistry.timer("hermes.human.review.time"));
        }
    }
}

@Entity
@Table(name = "review_tasks")
class ReviewTask {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private String reviewId;
    private String decisionId;
    private String assignedReviewer;
    private String reviewerId;
    private String priority;
    private String status;
    private String finalDecision;
    @Column(length = 2048)
    private String comments;
    private Instant dueAt;
    private Instant createdAt;
    private Instant completedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getReviewId() { return reviewId; }
    public void setReviewId(String reviewId) { this.reviewId = reviewId; }
    public String getDecisionId() { return decisionId; }
    public void setDecisionId(String decisionId) { this.decisionId = decisionId; }
    public String getAssignedReviewer() { return assignedReviewer; }
    public void setAssignedReviewer(String assignedReviewer) { this.assignedReviewer = assignedReviewer; }
    public String getReviewerId() { return reviewerId; }
    public void setReviewerId(String reviewerId) { this.reviewerId = reviewerId; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getFinalDecision() { return finalDecision; }
    public void setFinalDecision(String finalDecision) { this.finalDecision = finalDecision; }
    public String getComments() { return comments; }
    public void setComments(String comments) { this.comments = comments; }
    public Instant getDueAt() { return dueAt; }
    public void setDueAt(Instant dueAt) { this.dueAt = dueAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}

interface ReviewTaskRepository extends JpaRepository<ReviewTask, Long> {
    Optional<ReviewTask> findByReviewId(String reviewId);
    List<ReviewTask> findByStatusOrderByDueAtAsc(String status);
}
