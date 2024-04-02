/*
 * Copyright [2024], gematik GmbH
 *
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
 */

package de.gematik.demis.service.base.metrics;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

@SpringBootTest(
    classes = MeterAspectIntegrationTest.TestApp.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = "base.meter.aspect.enabled=true")
class MeterAspectIntegrationTest {

  @Autowired MyService service;

  @Autowired MeterRegistry meterRegistry;

  @Test
  void timedAnnotation() {
    service.doSomethingWithTimed();
    final Timer meter = meterRegistry.find(MyService.TIMER_NAME).timer();
    assertNotNull(meter);
  }

  @Test
  void countedAnnotation() {
    service.doSomethingWithCounted();
    final Counter meter = meterRegistry.find(MyService.COUNTER_NAME).counter();
    assertNotNull(meter);
  }

  @SpringBootApplication
  static class TestApp {
    @Bean
    MeterRegistry meterRegistry() {
      return new SimpleMeterRegistry();
    }
  }
}
