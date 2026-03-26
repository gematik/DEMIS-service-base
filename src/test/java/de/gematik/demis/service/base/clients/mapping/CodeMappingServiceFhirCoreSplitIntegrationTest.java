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
    classes = CodeMappingServiceFhirCoreSplitIntegrationTest.TestApplication.class,
    properties = {
      "demis.codemapping.enabled=true",
      "demis.codemapping.cache-reload-cron=*/30 * * * * *",
      "demis.codemapping.client.base-url=http://localhost:${wiremock.server.port}",
      "demis.codemapping.client.context-path=/",
      "demis.codemapping.concept-maps[0]=DiseaseA",
      "demis.codemapping.concept-maps[1]=LabA",
      "demis.codemapping.fhir-package-headers[0]=pkg-a",
      "demis.codemapping.fhir-package-headers[1]=pkg-b",
      "feature.flag.fhir.core.split=true"
    })
@AutoConfigureWireMock(port = 0)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class CodeMappingServiceFhirCoreSplitIntegrationTest {

  @SpringBootApplication
  @EnableFeignClients(clients = CodeMappingClient.class)
  static class TestApplication {}

  @Autowired private CodeMappingService codeMappingService;

  @BeforeEach
  void resetWireMock() {
    WireMock.reset();
  }

  @Test
  void shouldSendFhirPackageHeaderInRequests() {
    stubConceptMapWithHeader("DiseaseA", "pkg-a", "{\"d1\":\"mappedDisease\"}");
    stubConceptMapWithHeader("DiseaseA", "pkg-b", "{}");
    stubConceptMapWithHeader("LabA", "pkg-a", "{}");
    stubConceptMapWithHeader("LabA", "pkg-b", "{\"l1\":\"mappedLab\"}");

    codeMappingService.loadConceptMaps();

    verify(
        getRequestedFor(urlEqualTo("/conceptmap/DiseaseA"))
            .withHeader("x-fhir-package", equalTo("pkg-a")));
    verify(
        getRequestedFor(urlEqualTo("/conceptmap/DiseaseA"))
            .withHeader("x-fhir-package", equalTo("pkg-b")));
    verify(
        getRequestedFor(urlEqualTo("/conceptmap/LabA"))
            .withHeader("x-fhir-package", equalTo("pkg-a")));
    verify(
        getRequestedFor(urlEqualTo("/conceptmap/LabA"))
            .withHeader("x-fhir-package", equalTo("pkg-b")));
  }

  @Test
  void shouldLoadMappingsFromMultipleHeaders() {
    stubConceptMapWithHeader("DiseaseA", "pkg-a", "{\"d1\":\"mappedDisease\"}");
    stubConceptMapWithHeader("DiseaseA", "pkg-b", "{\"d2\":\"mappedDisease2\"}");
    stubConceptMapWithHeader("LabA", "pkg-a", "{\"l1\":\"mappedLab\"}");
    stubConceptMapWithHeader("LabA", "pkg-b", "{\"l2\":\"mappedLab2\"}");

    codeMappingService.loadConceptMaps();

    assertThat(codeMappingService.mapCode("d1")).isEqualTo("mappedDisease");
    assertThat(codeMappingService.mapCode("d2")).isEqualTo("mappedDisease2");
    assertThat(codeMappingService.mapCode("l1")).isEqualTo("mappedLab");
    assertThat(codeMappingService.mapCode("l2")).isEqualTo("mappedLab2");
  }

  @Test
  void shouldMergeResultsWhenOnlyOneHeaderReturnsData() {
    stubConceptMapWithHeader("DiseaseA", "pkg-a", "{\"d1\":\"mappedDisease\"}");
    stubConceptMapWithHeader("DiseaseA", "pkg-b", "{}");
    stubConceptMapWithHeader("LabA", "pkg-a", "{}");
    stubConceptMapWithHeader("LabA", "pkg-b", "{\"l1\":\"mappedLab\"}");

    codeMappingService.loadConceptMaps();

    assertThat(codeMappingService.mapCode("d1")).isEqualTo("mappedDisease");
    assertThat(codeMappingService.mapCode("l1")).isEqualTo("mappedLab");
  }

  @Test
  void shouldContinueWithNextHeaderWhenFirstHeaderFails() {
    stubConceptMapWithHeaderAndStatus("DiseaseA", "pkg-a", 500);
    stubConceptMapWithHeader("DiseaseA", "pkg-b", "{\"d1\":\"fromPkgB\"}");
    stubConceptMapWithHeader("LabA", "pkg-a", "{\"l1\":\"mappedLab\"}");
    stubConceptMapWithHeader("LabA", "pkg-b", "{}");

    codeMappingService.loadConceptMaps();

    assertThat(codeMappingService.mapCode("d1")).isEqualTo("fromPkgB");
    assertThat(codeMappingService.mapCode("l1")).isEqualTo("mappedLab");
  }

  @Test
  void shouldSkipConceptMapWhenAllHeadersFail() {
    stubConceptMapWithHeaderAndStatus("DiseaseA", "pkg-a", 500);
    stubConceptMapWithHeaderAndStatus("DiseaseA", "pkg-b", 500);
    stubConceptMapWithHeader("LabA", "pkg-a", "{\"l1\":\"mappedLab\"}");
    stubConceptMapWithHeader("LabA", "pkg-b", "{}");

    codeMappingService.loadConceptMaps();

    assertThat(codeMappingService.mapCode("l1")).isEqualTo("mappedLab");
    assertThat(codeMappingService.mapCode("d1")).isNull();
  }

  @Test
  void shouldRaiseExceptionWhenAllConceptMapsFailWithAllHeaders() {
    stubConceptMapWithHeaderAndStatus("DiseaseA", "pkg-a", 500);
    stubConceptMapWithHeaderAndStatus("DiseaseA", "pkg-b", 500);
    stubConceptMapWithHeaderAndStatus("LabA", "pkg-a", 500);
    stubConceptMapWithHeaderAndStatus("LabA", "pkg-b", 500);

    codeMappingService.loadConceptMaps();

    assertThatThrownBy(() -> codeMappingService.mapCode("missing"))
        .isInstanceOf(CodeMappingUnavailableException.class)
        .hasMessageContaining("not available");
  }

  @Test
  void shouldRaiseExceptionWhenAllConceptMapsReturnEmptyWithAllHeaders() {
    stubConceptMapWithHeader("DiseaseA", "pkg-a", "{}");
    stubConceptMapWithHeader("DiseaseA", "pkg-b", "{}");
    stubConceptMapWithHeader("LabA", "pkg-a", "{}");
    stubConceptMapWithHeader("LabA", "pkg-b", "{}");

    codeMappingService.loadConceptMaps();

    assertThatThrownBy(() -> codeMappingService.mapCode("missing"))
        .isInstanceOf(CodeMappingUnavailableException.class)
        .hasMessageContaining("not available");
  }

  @Test
  void shouldKeepFirstValueOnDuplicateKeyAcrossHeaders() {
    stubConceptMapWithHeader("DiseaseA", "pkg-a", "{\"d1\":\"first\"}");
    stubConceptMapWithHeader("DiseaseA", "pkg-b", "{\"d1\":\"second\"}");
    stubConceptMapWithHeader("LabA", "pkg-a", "{}");
    stubConceptMapWithHeader("LabA", "pkg-b", "{}");

    codeMappingService.loadConceptMaps();

    assertThat(codeMappingService.mapCode("d1")).isEqualTo("first");
  }

  private void stubConceptMapWithHeader(
      final String name, final String headerValue, final String body) {
    stubFor(
        get(urlEqualTo("/conceptmap/" + name))
            .withHeader("x-fhir-package", equalTo(headerValue))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(body)));
  }

  private void stubConceptMapWithHeaderAndStatus(
      final String name, final String headerValue, final int status) {
    stubFor(
        get(urlEqualTo("/conceptmap/" + name))
            .withHeader("x-fhir-package", equalTo(headerValue))
            .willReturn(aResponse().withStatus(status)));
  }
}
