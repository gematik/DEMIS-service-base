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

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class CodeMappingAutoConfigurationTest {

  @Configuration
  static class TestConfigEnabled {
    @Bean
    CodeMappingClient codeMappingClient() {
      return mock(CodeMappingClient.class);
    }

    @Bean
    CodeMappingService codeMappingService(CodeMappingClient client) {
      CodeMappingProperties props = new CodeMappingProperties();
      props.setEnabled(true);
      props.setCacheReloadCron("0 */5 * * * *");
      props.getClient().setBaseUrl("http://example");
      props.getClient().setContextPath("/");
      props.setConceptMaps(List.of("DiseaseA", "LabA"));
      return new CodeMappingService(client, props, ReloadableCache::new);
    }
  }

  @Test
  void shouldCreateServiceWhenEnabled() {
    new ApplicationContextRunner()
        .withUserConfiguration(TestConfigEnabled.class)
        .run(context -> assertThat(context).hasSingleBean(CodeMappingService.class));
  }

  @Test
  void shouldNotCreateServiceWhenDisabled() {
    new ApplicationContextRunner()
        .run(context -> assertThat(context).doesNotHaveBean(CodeMappingService.class));
  }
}
