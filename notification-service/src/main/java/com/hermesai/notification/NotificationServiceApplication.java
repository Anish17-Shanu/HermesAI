package com.hermesai.notification;

import com.hermesai.common.api.ApiContracts;
import com.hermesai.common.config.KafkaTopics;
import com.hermesai.common.events.EventSchemas;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@EnableKafka
@SpringBootApplication(scanBasePackages = "com.hermesai")
public class NotificationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}

@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController {

    private final NotificationApplicationService service;

    NotificationController(NotificationApplicationService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','REVIEWER')")
    public ResponseEntity<List<ApiContracts.NotificationView>> list() {
        return ResponseEntity.ok(service.list());
    }
}

@org.springframework.stereotype.Service
class NotificationApplicationService {

    private final List<ApiContracts.NotificationView> notifications = new ArrayList<ApiContracts.NotificationView>();
    private final KafkaTemplate<String, Object> kafkaTemplate;

    NotificationApplicationService(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(topics = KafkaTopics.REVIEW_REQUESTED, groupId = "notification-service")
    public void onReviewRequested(EventSchemas.ReviewRequestedEvent event) {
        ApiContracts.NotificationView notification = new ApiContracts.NotificationView();
        notification.channel = "EMAIL+WEBSOCKET";
        notification.recipient = event.assignedReviewer;
        notification.message = "Decision " + event.decisionId + " needs review before " + event.dueAt;
        notification.createdAt = Instant.now();
        synchronized (notifications) {
            notifications.add(notification);
        }

        EventSchemas.NotificationEvent emitted = new EventSchemas.NotificationEvent();
        emitted.eventId = UUID.randomUUID().toString();
        emitted.decisionId = event.decisionId;
        emitted.recipient = event.assignedReviewer;
        emitted.channel = notification.channel;
        emitted.message = notification.message;
        emitted.occurredAt = Instant.now();
        kafkaTemplate.send(KafkaTopics.NOTIFICATIONS, event.decisionId, emitted);
    }

    public List<ApiContracts.NotificationView> list() {
        synchronized (notifications) {
            return new ArrayList<ApiContracts.NotificationView>(notifications);
        }
    }
}
