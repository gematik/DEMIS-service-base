package de.gematik.demis.service.base.clients.mapping;

/*-
 * #%L
 * service-base
 * %%
 * Copyright (C) 2025 - 2026 gematik GmbH
 * %%
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the
 * European Commission – subsequent versions of the EUPL (the "Licence").
 * You may not use this work except in compliance with the Licence.
 *
 * You find a copy of the Licence in the "Licence" file or at
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either expressed or implied.
 * In case of changes by gematik find details in the "Readme" file.
 *
 * See the Licence for the specific language governing permissions and limitations under the Licence.
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik,
 * find details in the "Readme" file.
 * #L%
 */

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExponentialBackoffRetryTest {

  @Captor private ArgumentCaptor<Duration> durationCaptor;

  private ExponentialBackoffRetry retryWithShortDelays;

  @BeforeEach
  void setUp() {
    // use short delays for tests
    retryWithShortDelays =
        new ExponentialBackoffRetry(Duration.ofMillis(10), Duration.ofMillis(100), 5);
  }

  @Test
  void testSuccessfulFirstAttempt_NoRetry() {
    // Arrange
    Supplier<String> operation = () -> "success";
    Predicate<String> shouldRetry = result -> false;

    // Act
    String result = retryWithShortDelays.executeWithRetry("testOperation", operation, shouldRetry);

    // Assert
    assertThat(result).isEqualTo("success");
  }

  @Test
  void testRetryOnceBeforeSuccess() {
    // Arrange
    ExponentialBackoffRetry spyRetry = spy(retryWithShortDelays);

    // Mock sleep to avoid actual waiting
    doNothing().when(spyRetry).sleep(any(Duration.class));

    AtomicInteger callCount = new AtomicInteger(0);
    Supplier<String> operation =
        () -> {
          int count = callCount.incrementAndGet();
          return count == 1 ? "failed" : "success";
        };
    Predicate<String> shouldRetry = "failed"::equals;

    // Act
    String result = spyRetry.executeWithRetry("testOperation", operation, shouldRetry);

    // Assert
    assertThat(result).isEqualTo("success");
    assertThat(callCount.get()).isEqualTo(2); // first try + 1 retry
  }

  @Test
  void testMultipleRetriesWithExponentialBackoff() {
    // Arrange
    ExponentialBackoffRetry spyRetry = spy(retryWithShortDelays);

    // Mock sleep to avoid actual waiting
    doNothing().when(spyRetry).sleep(any(Duration.class));

    AtomicInteger callCount = new AtomicInteger(0);
    Supplier<String> operation =
        () -> {
          int count = callCount.incrementAndGet();
          return count < 4 ? "failed" : "success";
        };
    Predicate<String> shouldRetry = "failed"::equals;

    // Act
    String result = spyRetry.executeWithRetry("testOperation", operation, shouldRetry);

    // Assert
    assertThat(result).isEqualTo("success");
    assertThat(callCount.get()).isEqualTo(4); // 1 initial + 3 retries

    // verify exponential backoff: 10ms, 20ms, 40ms
    verify(spyRetry, times(3)).sleep(durationCaptor.capture());
    assertThat(durationCaptor.getAllValues())
        .containsExactly(Duration.ofMillis(10), Duration.ofMillis(20), Duration.ofMillis(40));
  }

  @Test
  void testMaxDelayIsRespected() {
    // Arrange
    ExponentialBackoffRetry spyRetry = spy(retryWithShortDelays);

    // Mock sleep to avoid actual waiting
    doNothing().when(spyRetry).sleep(any(Duration.class));

    AtomicInteger callCount = new AtomicInteger(0);
    Supplier<String> operation =
        () -> {
          int count = callCount.incrementAndGet();
          return count < 6 ? "failed" : "success";
        };
    Predicate<String> shouldRetry = "failed"::equals;

    // Act
    String result = spyRetry.executeWithRetry("testOperation", operation, shouldRetry);

    // Assert
    assertThat(result).isEqualTo("success");

    // verify that max delay is not exceeded
    // Delays: 10ms, 20ms, 40ms, 80ms, 100ms (instead of 160ms)
    verify(spyRetry, times(5)).sleep(durationCaptor.capture());
    assertThat(durationCaptor.getAllValues())
        .containsExactly(
            Duration.ofMillis(10),
            Duration.ofMillis(20),
            Duration.ofMillis(40),
            Duration.ofMillis(80),
            Duration.ofMillis(100));
  }

  @Test
  void testMaxAttemptsReached_ReturnsLastResult() {
    // Arrange
    ExponentialBackoffRetry retry =
        new ExponentialBackoffRetry(Duration.ofMillis(10), Duration.ofMillis(100), 3);
    ExponentialBackoffRetry spyRetry = spy(retry);

    // Mock sleep to avoid actual waiting
    doNothing().when(spyRetry).sleep(any(Duration.class));

    AtomicInteger callCount = new AtomicInteger(0);
    Supplier<String> operation =
        () -> {
          callCount.incrementAndGet();
          return "always-failed";
        };
    Predicate<String> shouldRetry = "always-failed"::equals;

    // Act
    String result = spyRetry.executeWithRetry("testOperation", operation, shouldRetry);

    // Assert
    assertThat(result).isEqualTo("always-failed");
    assertThat(callCount.get()).isEqualTo(4); // 1 initial attempt + max 3 additional attempts
  }

  @Test
  void testInterruptHandling() {
    // Arrange
    ExponentialBackoffRetry retry =
        new ExponentialBackoffRetry(Duration.ofMillis(10), Duration.ofMillis(100), 3) {
          @Override
          protected void sleep(Duration duration) {
            Thread.currentThread().interrupt(); // simulate interrupt
            super.sleep(duration);
          }
        };

    AtomicInteger callCount = new AtomicInteger(0);
    Supplier<String> operation =
        () -> {
          int count = callCount.incrementAndGet();
          return count == 1 ? "failed" : "success";
        };
    Predicate<String> shouldRetry = "failed"::equals;

    // Act & Assert
    assertThatThrownBy(() -> retry.executeWithRetry("testOperation", operation, shouldRetry))
        .isInstanceOf(RetryInterruptedException.class)
        .hasMessage("Retry interrupted")
        .hasCauseInstanceOf(InterruptedException.class);

    // verify interrupt flag is set
    assertThat(Thread.interrupted()).isTrue();
  }

  @Test
  void testNullResultHandling() {
    // Arrange
    ExponentialBackoffRetry spyRetry = spy(retryWithShortDelays);

    // Mock sleep to avoid actual waiting
    doNothing().when(spyRetry).sleep(any(Duration.class));

    AtomicInteger callCount = new AtomicInteger(0);
    Supplier<String> operation =
        () -> {
          int count = callCount.incrementAndGet();
          return count == 1 ? null : "success";
        };
    Predicate<String> shouldRetry = Objects::isNull;

    // Act
    String result = spyRetry.executeWithRetry("testOperation", operation, shouldRetry);

    // Assert
    assertThat(result).isEqualTo("success");
    assertThat(callCount.get()).isEqualTo(2);
  }

  @Test
  void testDifferentReturnTypes_Integer() {
    // Arrange
    ExponentialBackoffRetry spyRetry = spy(retryWithShortDelays);

    // Mock sleep to avoid actual waiting
    doNothing().when(spyRetry).sleep(any(Duration.class));

    AtomicInteger callCount = new AtomicInteger(0);
    Supplier<Integer> operation = () -> callCount.incrementAndGet() < 3 ? -1 : 42;
    Predicate<Integer> shouldRetry = result -> result == -1;

    // Act
    Integer result = spyRetry.executeWithRetry("testOperation", operation, shouldRetry);

    // Assert
    assertThat(result).isEqualTo(42);
    assertThat(callCount.get()).isEqualTo(3);
  }

  @Test
  void testDifferentReturnTypes_CustomObject() {
    // Arrange
    record Response(boolean success, String message) {}

    ExponentialBackoffRetry spyRetry = spy(retryWithShortDelays);

    // Mock sleep to avoid actual waiting
    doNothing().when(spyRetry).sleep(any(Duration.class));

    AtomicInteger callCount = new AtomicInteger(0);
    Supplier<Response> operation =
        () -> {
          int count = callCount.incrementAndGet();
          return count < 2 ? new Response(false, "error") : new Response(true, "OK");
        };
    Predicate<Response> shouldRetry = response -> !response.success();

    // Act
    Response result = spyRetry.executeWithRetry("testOperation", operation, shouldRetry);

    // Assert
    assertThat(result.success()).isTrue();
    assertThat(result.message()).isEqualTo("OK");
    assertThat(callCount.get()).isEqualTo(2);
  }

  @Test
  void testOperationThrowsException_NotCaught() {
    // Arrange
    Supplier<String> operation =
        () -> {
          throw new IllegalStateException("Operation failed");
        };
    Predicate<String> shouldRetry = result -> false;

    // Act & Assert
    assertThatThrownBy(
            () -> retryWithShortDelays.executeWithRetry("testOperation", operation, shouldRetry))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Operation failed");
  }

  @Test
  void testNoMaxAttempts_ContinuesUntilSuccess() {
    // Arrange
    ExponentialBackoffRetry retry =
        new ExponentialBackoffRetry(
            Duration.ofMillis(10), Duration.ofMillis(100), null); // No limit
    ExponentialBackoffRetry spyRetry = spy(retry);

    // Mock sleep to avoid actual waiting
    doNothing().when(spyRetry).sleep(any(Duration.class));

    AtomicInteger callCount = new AtomicInteger(0);
    Supplier<String> operation =
        () -> {
          int count = callCount.incrementAndGet();
          return count < 10 ? "failed" : "success";
        };
    Predicate<String> shouldRetry = "failed"::equals;

    // Act
    String result = spyRetry.executeWithRetry("testOperation", operation, shouldRetry);

    // Assert
    assertThat(result).isEqualTo("success");
    assertThat(callCount.get()).isEqualTo(10); // 1 initial + 9 retries
  }

  @Test
  void testRetryCounterIncrementsCorrectly() {
    // Arrange
    ExponentialBackoffRetry spyRetry = spy(retryWithShortDelays);

    // Mock sleep to avoid actual waiting
    doNothing().when(spyRetry).sleep(any(Duration.class));

    AtomicInteger callCount = new AtomicInteger(0);
    Supplier<String> operation =
        () -> {
          int count = callCount.incrementAndGet();
          return count < 5 ? "failed" : "success";
        };
    Predicate<String> shouldRetry = "failed"::equals;

    // Act
    String result = spyRetry.executeWithRetry("testOperation", operation, shouldRetry);

    // Assert
    assertThat(result).isEqualTo("success");
    // call sleep method 4 times
    verify(spyRetry, times(4)).sleep(any());
  }

  @Test
  void testPreciseDelayCalculation() {
    // Arrange
    ExponentialBackoffRetry retry =
        new ExponentialBackoffRetry(Duration.ofSeconds(1), Duration.ofSeconds(10), 5);
    ExponentialBackoffRetry spyRetry = spy(retry);

    // Mock sleep to avoid actual waiting
    doNothing().when(spyRetry).sleep(any(Duration.class));

    AtomicInteger callCount = new AtomicInteger(0);
    Supplier<String> operation =
        () -> {
          int count = callCount.incrementAndGet();
          return count < 5 ? "failed" : "success";
        };
    Predicate<String> shouldRetry = "failed"::equals;

    // Act
    String result = spyRetry.executeWithRetry("testOperation", operation, shouldRetry);

    // Assert
    assertThat(result).isEqualTo("success");

    // verify exact delays: 1s, 2s, 4s, 8s
    verify(spyRetry, times(4)).sleep(durationCaptor.capture());
    assertThat(durationCaptor.getAllValues())
        .containsExactly(
            Duration.ofSeconds(1),
            Duration.ofSeconds(2),
            Duration.ofSeconds(4),
            Duration.ofSeconds(8));
  }
}
