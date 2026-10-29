package org.hcz.common.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class MdcTaskDecoratorTests {

    private final MdcTaskDecorator decorator = new MdcTaskDecorator();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void copiesSubmittingThreadContextIntoTask() {
        MDC.put("requestId", "request-123");
        AtomicReference<String> observedRequestId = new AtomicReference<>();
        Runnable decorated = decorator.decorate(
                () -> observedRequestId.set(MDC.get("requestId")));

        MDC.clear();
        decorated.run();

        assertThat(observedRequestId).hasValue("request-123");
        assertThat(MDC.getCopyOfContextMap()).isNull();
    }

    @Test
    void restoresWorkerThreadContextAfterTask() {
        MDC.put("requestId", "submitting-request");
        Runnable decorated = decorator.decorate(
                () -> assertThat(MDC.get("requestId")).isEqualTo("submitting-request"));

        MDC.put("requestId", "worker-request");
        decorated.run();

        assertThat(MDC.get("requestId")).isEqualTo("worker-request");
    }
}
