package com.hermesai.workflow;

import com.hermesai.common.api.ApiContracts;
import com.hermesai.common.config.KafkaTopics;
import com.hermesai.common.events.EventSchemas;
import com.hermesai.common.model.DomainTypes;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@EnableKafka
@SpringBootApplication(scanBasePackages = "com.hermesai")
public class WorkflowOrchestratorApplication {
    public static void main(String[] args) {
        SpringApplication.run(WorkflowOrchestratorApplication.class, args);
    }
}

@RestController
@RequestMapping("/api/v1/workflows")
class WorkflowController {

    private final WorkflowService service;

    WorkflowController(WorkflowService service) {
        this.service = service;
    }

    @GetMapping("/{decisionId}")
    @PreAuthorize("hasAnyRole('ADMIN','REVIEWER','SYSTEM')")
    public ResponseEntity<ApiContracts.WorkflowView> get(@PathVariable String decisionId) {
        return ResponseEntity.ok(service.getWorkflow(decisionId));
    }
}

@org.springframework.stereotype.Service
class WorkflowService {

    private final WorkflowRepository repository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    WorkflowService(WorkflowRepository repository, KafkaTemplate<String, Object> kafkaTemplate) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(topics = KafkaTopics.DECISION_EVALUATED, groupId = "workflow-orchestrator-service")
    @Transactional
    public void onDecisionEvaluated(EventSchemas.DecisionEvaluatedEvent event) {
        WorkflowInstance workflow = repository.findByDecisionId(event.decisionId).orElse(new WorkflowInstance());
        workflow.setWorkflowId(workflow.getWorkflowId() == null ? UUID.randomUUID().toString() : workflow.getWorkflowId());
        workflow.setDecisionId(event.decisionId);
        workflow.setState(event.requiresHumanReview
                ? DomainTypes.WorkflowState.HUMAN_PENDING.name()
                : DomainTypes.WorkflowState.COMPLETED.name());
        workflow.setLastUpdatedBy("decision-service");
        workflow.setUpdatedAt(Instant.now());
        repository.save(workflow);

        if (event.requiresHumanReview) {
            EventSchemas.ReviewRequestedEvent reviewRequested = new EventSchemas.ReviewRequestedEvent();
            reviewRequested.decisionId = event.decisionId;
            reviewRequested.reviewId = UUID.randomUUID().toString();
            reviewRequested.assignedReviewer = "reviewer-queue";
            reviewRequested.priority = "HIGH_RISK".equalsIgnoreCase(event.riskCategory) ? "URGENT" : "HIGH";
            reviewRequested.dueAt = Instant.now().plus(30, ChronoUnit.MINUTES);
            reviewRequested.occurredAt = Instant.now();
            kafkaTemplate.send(KafkaTopics.REVIEW_REQUESTED, event.decisionId, reviewRequested);
        }
        publishWorkflowState(workflow, "decision-service");
    }

    @KafkaListener(topics = KafkaTopics.REVIEW_COMPLETED, groupId = "workflow-orchestrator-service")
    @Transactional
    public void onReviewCompleted(EventSchemas.ReviewCompletedEvent event) {
        WorkflowInstance workflow = repository.findByDecisionId(event.decisionId)
                .orElseThrow(() -> new IllegalArgumentException("Workflow not found"));
        workflow.setState("REJECT".equalsIgnoreCase(event.finalDecision)
                ? DomainTypes.WorkflowState.REJECTED.name()
                : DomainTypes.WorkflowState.APPROVED.name());
        workflow.setLastUpdatedBy(event.reviewerId);
        workflow.setUpdatedAt(Instant.now());
        repository.save(workflow);
        publishWorkflowState(workflow, event.reviewerId);

        workflow.setState(DomainTypes.WorkflowState.COMPLETED.name());
        workflow.setLastUpdatedBy("workflow-service");
        workflow.setUpdatedAt(Instant.now());
        repository.save(workflow);
        publishWorkflowState(workflow, "workflow-service");
    }

    public ApiContracts.WorkflowView getWorkflow(String decisionId) {
        WorkflowInstance workflow = repository.findByDecisionId(decisionId)
                .orElseThrow(() -> new IllegalArgumentException("Workflow not found"));
        ApiContracts.WorkflowView view = new ApiContracts.WorkflowView();
        view.workflowId = workflow.getWorkflowId();
        view.decisionId = workflow.getDecisionId();
        view.state = DomainTypes.WorkflowState.valueOf(workflow.getState());
        view.lastUpdatedBy = workflow.getLastUpdatedBy();
        view.updatedAt = workflow.getUpdatedAt();
        return view;
    }

    private void publishWorkflowState(WorkflowInstance workflow, String actor) {
        EventSchemas.WorkflowStateChangedEvent changed = new EventSchemas.WorkflowStateChangedEvent();
        changed.workflowId = workflow.getWorkflowId();
        changed.decisionId = workflow.getDecisionId();
        changed.state = DomainTypes.WorkflowState.valueOf(workflow.getState());
        changed.actor = actor;
        changed.occurredAt = Instant.now();
        kafkaTemplate.send(KafkaTopics.WORKFLOW_CHANGED, workflow.getDecisionId(), changed);

        EventSchemas.AuditEvent audit = new EventSchemas.AuditEvent();
        audit.auditId = UUID.randomUUID().toString();
        audit.decisionId = workflow.getDecisionId();
        audit.actor = actor;
        audit.eventType = "WORKFLOW_" + workflow.getState();
        audit.payload = workflow.getWorkflowId();
        audit.occurredAt = Instant.now();
        kafkaTemplate.send(KafkaTopics.AUDIT_EVENTS, workflow.getDecisionId(), audit);
    }
}

@Entity
@Table(name = "workflow_instances")
class WorkflowInstance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private String workflowId;
    @Column(nullable = false, unique = true)
    private String decisionId;
    private String state;
    private String lastUpdatedBy;
    private Instant updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getWorkflowId() { return workflowId; }
    public void setWorkflowId(String workflowId) { this.workflowId = workflowId; }
    public String getDecisionId() { return decisionId; }
    public void setDecisionId(String decisionId) { this.decisionId = decisionId; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getLastUpdatedBy() { return lastUpdatedBy; }
    public void setLastUpdatedBy(String lastUpdatedBy) { this.lastUpdatedBy = lastUpdatedBy; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

interface WorkflowRepository extends JpaRepository<WorkflowInstance, Long> {
    Optional<WorkflowInstance> findByDecisionId(String decisionId);
}
