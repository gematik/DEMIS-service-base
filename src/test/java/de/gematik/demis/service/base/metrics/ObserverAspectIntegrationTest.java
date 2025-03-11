package de.gematik.demis.service.base.metrics;

/*-
 * #%L
 * service-base
 * %%
 * Copyright (C) 2025 gematik GmbH
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
 * #L%
 */

import static io.micrometer.observation.tck.TestObservationRegistryAssert.assertThat;

import io.micrometer.observation.tck.TestObservationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    classes = ObserverAspectIntegrationTest.TestApp.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnableTestObservation
@AutoConfigureObservability
class ObserverAspectIntegrationTest {

  @Autowired MyService service;

  @Autowired TestObservationRegistry registry;

  @Test
  void observerAnnotation() {
    service.doSomethingWithObserved();
    assertThat(registry)
        .hasObservationWithNameEqualTo(MyService.OBSERVER_NAME)
        .that()
        .hasBeenStarted()
        .hasBeenStopped();
  }

  @SpringBootApplication
  static class TestApp {}
}
