package com.hermesai.review;

import com.hermesai.common.api.ApiContracts;
import com.hermesai.common.model.DomainTypes;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;

class HumanReviewServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void overrideMarksHumanCorrection() {
        ReviewTaskRepository repository = Mockito.mock(ReviewTaskRepository.class);
        ReviewTask task = new ReviewTask();
        task.setReviewId("rev-1");
        task.setDecisionId("dec-1");
        task.setStatus("PENDING");
        Mockito.when(repository.findByReviewId("rev-1")).thenReturn(Optional.of(task));
        Mockito.when(repository.save(Mockito.any(ReviewTask.class))).thenAnswer(invocation -> invocation.getArgument(0));

        HumanReviewApplicationService service = new HumanReviewApplicationService(
                repository,
                (KafkaTemplate<String, Object>) Mockito.mock(KafkaTemplate.class),
                Mockito.mock(StringRedisTemplate.class),
                new SimpleMeterRegistry());

        ApiContracts.ReviewActionRequest request = new ApiContracts.ReviewActionRequest();
        request.reviewerId = "reviewer-a";
        request.overrideDecision = "REJECT";
        request.comments = "manual override";

        ReviewTask updated = service.complete("rev-1", request, DomainTypes.ReviewAction.OVERRIDE);
        Assertions.assertEquals("REJECT", updated.getFinalDecision());
    }
}
