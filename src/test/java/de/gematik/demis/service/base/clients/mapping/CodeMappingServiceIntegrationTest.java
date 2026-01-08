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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(
    classes = CodeMappingServiceIntegrationTest.TestApplication.class,
    properties = {
      "demis.codemapping.enabled=true",
      "demis.codemapping.cache-reload-cron=*/30 * * * * *",
      "demis.codemapping.client.base-url=http://localhost:${wiremock.server.port}",
      "demis.codemapping.client.context-path=/",
      "demis.codemapping.disease.concept-maps[0]=DiseaseA",
      "demis.codemapping.laboratory.concept-maps[0]=LabA"
    })
@AutoConfigureWireMock(port = 0)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class CodeMappingServiceIntegrationTest {

  @SpringBootApplication
  @EnableFeignClients(clients = CodeMappingClient.class)
  static class TestApplication {

    @org.springframework.boot.test.context.TestConfiguration
    static class TestRetryConfiguration {

      @org.springframework.context.annotation.Bean
      @org.springframework.context.annotation.Primary
      CodeMappingService.RetryFactory retryFactory() {
        // Fast retry for tests: 1ms delays, max 3 attempts
        return () ->
            new ExponentialBackoffRetry(
                java.time.Duration.ofMillis(1), java.time.Duration.ofMillis(1), 3);
      }
    }
  }

  @Autowired private CodeMappingService codeMappingService;

  @BeforeEach
  void resetWireMock() {
    WireMock.reset();
  }

  @Test
  void shouldLoadMappingsFromRemoteService() {
    stubConceptMap("DiseaseA", "{\"d1\":\"mappedDisease\"}");
    stubConceptMap("LabA", "{\"l1\":\"mappedLab\"}");

    codeMappingService.loadConceptMaps();

    assertThat(codeMappingService.getMappedDiseaseCode("d1")).isEqualTo("mappedDisease");
    assertThat(codeMappingService.getMappedLaboratoryCode("l1")).isEqualTo("mappedLab");
  }

  @Test
  void shouldRaiseExceptionWhenConceptMapIsEmpty() {
    stubConceptMap("DiseaseA", "{}");
    stubConceptMap("LabA", "{\"l1\":\"mappedLab\"}");

    codeMappingService.loadConceptMaps();

    assertThatThrownBy(() -> codeMappingService.getMappedDiseaseCode("missing"))
        .isInstanceOf(CodeMappingUnavailableException.class)
        .hasMessageContaining("Disease");
  }

  private void stubConceptMap(final String name, final String body) {
    stubFor(
        get(urlEqualTo("/conceptmap/" + name))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(body)));
  }
}
