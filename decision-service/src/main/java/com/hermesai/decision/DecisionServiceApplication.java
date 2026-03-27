package com.hermesai.decision;

import com.hermesai.common.api.ApiContracts;
import com.hermesai.common.config.KafkaTopics;
import com.hermesai.common.events.EventSchemas;
import com.hermesai.common.model.DomainTypes;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@SpringBootApplication(scanBasePackages = "com.hermesai")
public class DecisionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DecisionServiceApplication.class, args);
    }

    @Bean
    public Tracer tracer() {
        return GlobalOpenTelemetry.getTracer("decision-service");
    }
}

@RestController
@RequestMapping("/api/v1/decisions")
class DecisionController {

    private final DecisionEngineService service;

    DecisionController(DecisionEngineService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SYSTEM')")
    public ResponseEntity<ApiContracts.DecisionResponse> create(
            @RequestBody ApiContracts.DecisionRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.process(request, idempotencyKey));
    }

    @GetMapping("/{decisionId}")
    @PreAuthorize("hasAnyRole('ADMIN','REVIEWER','SYSTEM')")
    public ResponseEntity<ApiContracts.DecisionResponse> get(@PathVariable String decisionId) {
        return ResponseEntity.ok(service.get(decisionId));
    }
}

@org.springframework.stereotype.Service
class DecisionEngineService {

    private static final Set<String> ALWAYS_REVIEW = new HashSet<String>(Arrays.asList("HIGH_RISK", "SANCTIONS"));

    private final DecisionRecordRepository repository;
    private final StringRedisTemplate redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    private final Tracer tracer;

    DecisionEngineService(DecisionRecordRepository repository,
                          StringRedisTemplate redisTemplate,
                          KafkaTemplate<String, Object> kafkaTemplate,
                          MeterRegistry meterRegistry,
                          Tracer tracer) {
        this.repository = repository;
        this.redisTemplate = redisTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.meterRegistry = meterRegistry;
        this.tracer = tracer;
    }

    @Transactional
    public ApiContracts.DecisionResponse process(ApiContracts.DecisionRequest request, String idempotencyKey) {
        Timer.Sample timer = Timer.start(meterRegistry);
        Span span = tracer.spanBuilder("decision.process").startSpan();
        try {
            if (request.externalReference == null || request.externalReference.trim().isEmpty()) {
                throw new IllegalArgumentException("externalReference is required");
            }
            String effectiveKey = idempotencyKey == null ? request.externalReference : idempotencyKey;
            String cacheKey = "decision:idempotency:" + effectiveKey;
            String existingDecisionId = redisTemplate.opsForValue().get(cacheKey);
            if (existingDecisionId != null) {
                return get(existingDecisionId);
            }

            double confidence = calculateConfidence(request);
            DomainTypes.DecisionOutcome decision = confidence >= 0.50
                    ? DomainTypes.DecisionOutcome.APPROVE : DomainTypes.DecisionOutcome.REJECT;
            boolean requiresHuman = Boolean.TRUE.equals(request.synchronousReview)
                    || confidence < 0.85
                    || ALWAYS_REVIEW.contains(normalize(request.riskCategory));

            DecisionRecord record = new DecisionRecord();
            record.setDecisionId(UUID.randomUUID().toString());
            record.setExternalReference(request.externalReference);
            record.setRiskCategory(normalize(request.riskCategory));
            record.setInputPayload(request.payload);
            record.setAmount(request.amount == null ? 0.0 : request.amount);
            record.setAiDecision(decision.name());
            record.setConfidenceScore(confidence);
            record.setRequiresHumanReview(requiresHuman);
            record.setWorkflowState(requiresHuman
                    ? DomainTypes.WorkflowState.HUMAN_PENDING.name()
                    : DomainTypes.WorkflowState.COMPLETED.name());
            record.setCreatedAt(Instant.now());
            record.setUpdatedAt(Instant.now());
            try {
                repository.save(record);
            } catch (DataIntegrityViolationException duplicate) {
                return toResponse(repository.findByExternalReference(request.externalReference).orElseThrow(
                        () -> duplicate));
            }

            redisTemplate.opsForValue().set(cacheKey, record.getDecisionId());

            EventSchemas.DecisionEvaluatedEvent event = new EventSchemas.DecisionEvaluatedEvent();
            event.decisionId = record.getDecisionId();
            event.externalReference = record.getExternalReference();
            event.aiDecision = record.getAiDecision();
            event.confidenceScore = record.getConfidenceScore();
            event.requiresHumanReview = record.isRequiresHumanReview();
            event.riskCategory = record.getRiskCategory();
            event.payloadSnapshot = record.getInputPayload();
            event.occurredAt = Instant.now();
            kafkaTemplate.send(KafkaTopics.DECISION_EVALUATED, record.getDecisionId(), event);

            EventSchemas.AuditEvent audit = new EventSchemas.AuditEvent();
            audit.auditId = UUID.randomUUID().toString();
            audit.decisionId = record.getDecisionId();
            audit.actor = "decision-service";
            audit.eventType = "AI_DECISION_CREATED";
            audit.payload = "decision=" + record.getAiDecision() + ",confidence=" + record.getConfidenceScore();
            audit.occurredAt = Instant.now();
            kafkaTemplate.send(KafkaTopics.AUDIT_EVENTS, record.getDecisionId(), audit);

            meterRegistry.counter("hermes.decision.approval.rate", "decision", decision.name()).increment();
            return toResponse(record);
        } finally {
            span.end();
            timer.stop(meterRegistry.timer("hermes.decision.latency"));
        }
    }

    public ApiContracts.DecisionResponse get(String decisionId) {
        return toResponse(repository.findByDecisionId(decisionId)
                .orElseThrow(() -> new IllegalArgumentException("Decision not found")));
    }

    double calculateConfidence(ApiContracts.DecisionRequest request) {
        String payload = request.payload == null ? "" : request.payload;
        double payloadSignal = Math.abs(payload.hashCode() % 30) / 100.0;
        double amountSignal = request.amount == null ? 0.05 : Math.min(request.amount / 100000.0, 0.25);
        double riskPenalty = ALWAYS_REVIEW.contains(normalize(request.riskCategory)) ? 0.30 : 0.0;
        double score = 0.95 - payloadSignal - amountSignal - riskPenalty;
        return Math.max(0.15, Math.min(0.99, score));
    }

    private ApiContracts.DecisionResponse toResponse(DecisionRecord record) {
        ApiContracts.DecisionResponse response = new ApiContracts.DecisionResponse();
        response.decisionId = record.getDecisionId();
        response.decision = DomainTypes.DecisionOutcome.valueOf(record.getAiDecision());
        response.confidenceScore = record.getConfidenceScore();
        response.requiresHumanReview = record.isRequiresHumanReview();
        response.workflowState = DomainTypes.WorkflowState.valueOf(record.getWorkflowState());
        response.createdAt = record.getCreatedAt();
        return response;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }
}

@Entity
@Table(name = "decision_records")
class DecisionRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private String decisionId;
    @Column(nullable = false, unique = true)
    private String externalReference;
    private String riskCategory;
    @Column(length = 2048)
    private String inputPayload;
    private double amount;
    private String aiDecision;
    private double confidenceScore;
    private boolean requiresHumanReview;
    private String workflowState;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getDecisionId() { return decisionId; }
    public void setDecisionId(String decisionId) { this.decisionId = decisionId; }
    public String getExternalReference() { return externalReference; }
    public void setExternalReference(String externalReference) { this.externalReference = externalReference; }
    public String getRiskCategory() { return riskCategory; }
    public void setRiskCategory(String riskCategory) { this.riskCategory = riskCategory; }
    public String getInputPayload() { return inputPayload; }
    public void setInputPayload(String inputPayload) { this.inputPayload = inputPayload; }
    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }
    public String getAiDecision() { return aiDecision; }
    public void setAiDecision(String aiDecision) { this.aiDecision = aiDecision; }
    public double getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(double confidenceScore) { this.confidenceScore = confidenceScore; }
    public boolean isRequiresHumanReview() { return requiresHumanReview; }
    public void setRequiresHumanReview(boolean requiresHumanReview) { this.requiresHumanReview = requiresHumanReview; }
    public String getWorkflowState() { return workflowState; }
    public void setWorkflowState(String workflowState) { this.workflowState = workflowState; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

interface DecisionRecordRepository extends JpaRepository<DecisionRecord, Long> {
    Optional<DecisionRecord> findByDecisionId(String decisionId);
    Optional<DecisionRecord> findByExternalReference(String externalReference);
}

@RestControllerAdvice
class DecisionExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<String> handle(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(exception.getMessage());
    }
}
