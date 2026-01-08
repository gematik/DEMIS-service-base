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
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CodeMappingAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(CodeMappingAutoConfiguration.class))
          .withBean(CodeMappingClient.class, () -> mock(CodeMappingClient.class))
          .withPropertyValues(
              "demis.codemapping.enabled=true",
              "demis.codemapping.cache-reload-cron=0 */5 * * * *",
              "demis.codemapping.client.base-url=http://example",
              "demis.codemapping.client.context-path=/",
              "demis.codemapping.disease.concept-maps[0]=DiseaseA",
              "demis.codemapping.laboratory.concept-maps[0]=LabA");

  @Test
  void shouldCreateServiceWhenEnabled() {
    contextRunner.run(context -> assertThat(context).hasSingleBean(CodeMappingService.class));
  }

  @Test
  void shouldNotCreateServiceWhenDisabled() {
    contextRunner
        .withPropertyValues("demis.codemapping.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean(CodeMappingService.class));
  }
}
