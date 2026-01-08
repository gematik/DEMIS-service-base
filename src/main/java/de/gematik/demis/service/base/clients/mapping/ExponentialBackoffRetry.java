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

import java.time.Duration;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ExponentialBackoffRetry {
  private final Duration initialDelay;
  private final Duration maxDelay;
  private final Integer maxAttempts;

  public ExponentialBackoffRetry(Duration initialDelay, Duration maxDelay, Integer maxAttempts) {
    this.initialDelay = initialDelay;
    this.maxDelay = maxDelay;
    this.maxAttempts = maxAttempts;
  }

  public <T> T executeWithRetry(
      String operationName, Supplier<T> operation, java.util.function.Predicate<T> shouldRetry) {

    T result = operation.get();
    if (!shouldRetry.test(result)) {
      return result;
    }

    Duration currentDelay = initialDelay;
    int attempt = 1;

    while (shouldRetry.test(result)) {
      if (maxAttempts != null && attempt > maxAttempts) {
        log.warn(
            "Max retry attempts ({}) reached for {} - returning last result",
            maxAttempts,
            operationName);
        return result;
      }

      log.info("Retry attempt {} for {} - waiting {}", attempt, operationName, currentDelay);

      sleep(currentDelay);

      result = operation.get();

      if (currentDelay.compareTo(maxDelay) < 0) {
        currentDelay = currentDelay.multipliedBy(2);
        if (currentDelay.compareTo(maxDelay) > 0) {
          currentDelay = maxDelay;
        }
      }

      attempt++;
    }

    return result;
  }

  protected void sleep(Duration duration) {
    try {
      Thread.sleep(duration.toMillis());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RetryInterruptedException("Retry interrupted", e);
    }
  }
}
