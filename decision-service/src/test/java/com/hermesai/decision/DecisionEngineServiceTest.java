package com.hermesai.decision;

import com.hermesai.common.api.ApiContracts;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.GlobalOpenTelemetry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;

class DecisionEngineServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void highRiskRequestsRequireHumanReview() {
        DecisionEngineService service = new DecisionEngineService(
                Mockito.mock(DecisionRecordRepository.class),
                Mockito.mock(StringRedisTemplate.class),
                (KafkaTemplate<String, Object>) Mockito.mock(KafkaTemplate.class),
                new SimpleMeterRegistry(),
                GlobalOpenTelemetry.getTracer("test"));

        ApiContracts.DecisionRequest request = new ApiContracts.DecisionRequest();
        request.externalReference = "risk-1";
        request.riskCategory = "HIGH_RISK";
        request.payload = "customer";
        request.amount = 2500.0;

        double confidence = service.calculateConfidence(request);
        Assertions.assertTrue(confidence < 0.85);
    }
}
