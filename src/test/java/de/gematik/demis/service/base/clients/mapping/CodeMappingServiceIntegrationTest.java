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
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
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
      "demis.codemapping.concept-maps[0]=DiseaseA",
      "demis.codemapping.concept-maps[1]=LabA",
      "feature.flag.fhir.core.split=false"
    })
@AutoConfigureWireMock(port = 0)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class CodeMappingServiceIntegrationTest {

  @SpringBootApplication
  @EnableFeignClients(clients = CodeMappingClient.class)
  static class TestApplication {}

  @Autowired private CodeMappingService codeMappingService;

  @BeforeEach
  void resetWireMock() {
    WireMock.reset();
  }

  @Test
  void shouldLoadMappingsFromRemoteService() {
    stubConceptMapWithHeader("DiseaseA", "{\"d1\":\"mappedDisease\"}");
    stubConceptMapWithHeader("LabA", "{\"l1\":\"mappedLab\"}");

    codeMappingService.loadConceptMaps();

    assertThat(codeMappingService.mapCode("d1")).isEqualTo("mappedDisease");
    assertThat(codeMappingService.mapCode("l1")).isEqualTo("mappedLab");
  }

  @Test
  void shouldRaiseExceptionWhenConceptMapIsEmpty() {
    stubConceptMapWithHeader("DiseaseA", "{}");
    stubConceptMapWithHeader("LabA", "{}");

    codeMappingService.loadConceptMaps();

    assertThatThrownBy(() -> codeMappingService.mapCode("missing"))
        .isInstanceOf(CodeMappingUnavailableException.class)
        .hasMessageContaining("not available");
  }

  @Test
  void shouldFallBackToLegacyCallOn403() {
    stubConceptMapWithHeaderReturns403("DiseaseA");
    stubConceptMapWithoutHeader("DiseaseA", "{\"d1\":\"fallbackDisease\"}");
    stubConceptMapWithHeaderReturns403("LabA");
    stubConceptMapWithoutHeader("LabA", "{\"l1\":\"fallbackLab\"}");

    codeMappingService.loadConceptMaps();

    assertThat(codeMappingService.mapCode("d1")).isEqualTo("fallbackDisease");
    assertThat(codeMappingService.mapCode("l1")).isEqualTo("fallbackLab");

    verify(
        getRequestedFor(urlEqualTo("/conceptmap/DiseaseA"))
            .withHeader("x-fhir-profile", equalTo("fhir-profile-snapshots")));
    verify(getRequestedFor(urlEqualTo("/conceptmap/DiseaseA")).withoutHeader("x-fhir-profile"));
  }

  @Test
  void shouldFallBackPartiallyWhenOnly403ForSomeMaps() {
    stubConceptMapWithHeaderReturns403("DiseaseA");
    stubConceptMapWithoutHeader("DiseaseA", "{\"d1\":\"fallbackDisease\"}");
    stubConceptMapWithHeader("LabA", "{\"l1\":\"directLab\"}");

    codeMappingService.loadConceptMaps();

    assertThat(codeMappingService.mapCode("d1")).isEqualTo("fallbackDisease");
    assertThat(codeMappingService.mapCode("l1")).isEqualTo("directLab");
  }

  private void stubConceptMapWithHeader(final String name, final String body) {
    stubFor(
        get(urlEqualTo("/conceptmap/" + name))
            .withHeader("x-fhir-profile", equalTo("fhir-profile-snapshots"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(body)));
  }

  private void stubConceptMapWithHeaderReturns403(final String name) {
    stubFor(
        get(urlEqualTo("/conceptmap/" + name))
            .withHeader("x-fhir-profile", equalTo("fhir-profile-snapshots"))
            .willReturn(aResponse().withStatus(403).withBody("RBAC: access denied")));
  }

  private void stubConceptMapWithoutHeader(final String name, final String body) {
    stubFor(
        get(urlEqualTo("/conceptmap/" + name))
            .withHeader("x-fhir-profile", absent())
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(body)));
  }
}
