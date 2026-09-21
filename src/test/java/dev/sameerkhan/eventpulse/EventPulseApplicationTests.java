package dev.sameerkhan.eventpulse;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class EventPulseApplicationTests {
  @Test void duplicateEventIsProcessedOnceAndFailuresReachDeadLetter() {
    var service = new EventPulseApplication.DeliveryService();
    var request = new EventPulseApplication.NotificationRequest("order-1001", "sameer@example.com",
        EventPulseApplication.Channel.EMAIL, "Order confirmed");
    var first = service.accept(request);
    assertThat(service.accept(request).id()).isEqualTo(first.id());
    service.retry(first.id()); service.retry(first.id());
    var finalAttempt = service.retry(first.id());
    assertThat(finalAttempt.status()).isEqualTo("DEAD_LETTER");
  }
}
