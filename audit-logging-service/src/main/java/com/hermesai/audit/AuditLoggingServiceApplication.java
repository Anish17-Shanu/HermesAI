package com.hermesai.audit;

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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@EnableKafka
@SpringBootApplication(scanBasePackages = "com.hermesai")
public class AuditLoggingServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuditLoggingServiceApplication.class, args);
    }
}

@RestController
@RequestMapping("/api/v1/audit")
class AuditController {

    private final AuditService service;

    AuditController(AuditService service) {
        this.service = service;
    }

    @GetMapping("/{decisionId}")
    @PreAuthorize("hasAnyRole('ADMIN','REVIEWER','SYSTEM')")
    public ResponseEntity<List<AuditEntry>> list(@PathVariable String decisionId) {
        return ResponseEntity.ok(service.listByDecision(decisionId));
    }
}

@org.springframework.stereotype.Service
class AuditService {

    private final AuditRepository repository;

    AuditService(AuditRepository repository) {
        this.repository = repository;
    }

    @KafkaListener(topics = KafkaTopics.AUDIT_EVENTS, groupId = "audit-logging-service")
    @Transactional
    public void onAuditEvent(EventSchemas.AuditEvent event) {
        AuditEntry entry = new AuditEntry();
        entry.setAuditId(event.auditId);
        entry.setDecisionId(event.decisionId);
        entry.setActor(event.actor);
        entry.setEventType(event.eventType);
        entry.setPayload(event.payload);
        entry.setOccurredAt(event.occurredAt == null ? Instant.now() : event.occurredAt);
        repository.save(entry);
    }

    public List<AuditEntry> listByDecision(String decisionId) {
        return repository.findByDecisionIdOrderByOccurredAtAsc(decisionId);
    }
}

@Entity
@Table(name = "audit_entries")
class AuditEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(unique = true)
    private String auditId;
    private String decisionId;
    private String actor;
    private String eventType;
    @Column(length = 2048)
    private String payload;
    private Instant occurredAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAuditId() { return auditId; }
    public void setAuditId(String auditId) { this.auditId = auditId; }
    public String getDecisionId() { return decisionId; }
    public void setDecisionId(String decisionId) { this.decisionId = decisionId; }
    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
}

interface AuditRepository extends JpaRepository<AuditEntry, Long> {
    List<AuditEntry> findByDecisionIdOrderByOccurredAtAsc(String decisionId);
}
