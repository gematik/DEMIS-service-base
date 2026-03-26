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

import static de.gematik.demis.service.base.clients.mapping.CodeMappingService.DEFAULT_FHIR_PACKAGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.gematik.demis.service.base.error.ServiceCallException;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CodeMappingServiceTest {

  private CodeMappingClient client;
  private CodeMappingProperties properties;
  private CodeMappingService.ReloadableCacheFactory cacheFactory;

  @BeforeEach
  void setUp() {
    client = mock(CodeMappingClient.class);
    properties = new CodeMappingProperties();
    properties.setCacheReloadCron("0 */5 * * * *");
    properties.getClient().setBaseUrl("http://example");
    properties.getClient().setContextPath("/");
    properties.setConceptMaps(List.of("DiseaseA", "LabA"));
    cacheFactory = ReloadableCache::new;
  }

  @Test
  void shouldLoadMappingsAndMapDiseaseCode() {
    when(client.getConceptMapWithPackageHeader("DiseaseA", DEFAULT_FHIR_PACKAGE))
        .thenReturn(Map.of("d1", "mapped"));
    when(client.getConceptMapWithPackageHeader("LabA", DEFAULT_FHIR_PACKAGE)).thenReturn(Map.of());

    var service = new CodeMappingService(client, properties, cacheFactory, false);
    service.loadConceptMaps();

    assertThat(service.mapCode("d1")).isEqualTo("mapped");
  }

  @Test
  void shouldLoadMappingsAndMapLaboratoryCode() {
    when(client.getConceptMapWithPackageHeader("DiseaseA", DEFAULT_FHIR_PACKAGE))
        .thenReturn(Map.of());
    when(client.getConceptMapWithPackageHeader("LabA", DEFAULT_FHIR_PACKAGE))
        .thenReturn(Map.of("l1", "mappedLab"));

    var service = new CodeMappingService(client, properties, cacheFactory, false);
    service.loadConceptMaps();

    assertThat(service.mapCode("l1")).isEqualTo("mappedLab");
  }

  @Test
  void shouldKeepFirstValueWhenDuplicateKeyAppears() {
    properties.setConceptMaps(List.of("DiseaseA", "DiseaseB"));
    when(client.getConceptMapWithPackageHeader("DiseaseA", DEFAULT_FHIR_PACKAGE))
        .thenReturn(Map.of("d1", "first"));
    when(client.getConceptMapWithPackageHeader("DiseaseB", DEFAULT_FHIR_PACKAGE))
        .thenReturn(Map.of("d1", "second"));

    var service = new CodeMappingService(client, properties, cacheFactory, false);
    service.loadConceptMaps();

    assertThat(service.mapCode("d1")).isEqualTo("first");
  }

  @Test
  void shouldThrowWhenCacheEmpty() {
    when(client.getConceptMapWithPackageHeader("DiseaseA", DEFAULT_FHIR_PACKAGE))
        .thenReturn(Map.of());
    when(client.getConceptMapWithPackageHeader("LabA", DEFAULT_FHIR_PACKAGE)).thenReturn(Map.of());

    var service = new CodeMappingService(client, properties, cacheFactory, false);
    service.loadConceptMaps();

    assertThatThrownBy(() -> service.mapCode("d1"))
        .isInstanceOf(CodeMappingUnavailableException.class)
        .hasMessageContaining("not available");
  }

  @Test
  void shouldHandleMissingConceptMapGracefully() {
    properties.setConceptMaps(List.of("DiseaseA", "MissingMap", "LabA"));
    when(client.getConceptMapWithPackageHeader("DiseaseA", DEFAULT_FHIR_PACKAGE))
        .thenReturn(Map.of("d1", "mappedDisease"));
    when(client.getConceptMapWithPackageHeader("MissingMap", DEFAULT_FHIR_PACKAGE))
        .thenThrow(new RuntimeException("404 Not Found"));
    when(client.getConceptMapWithPackageHeader("LabA", DEFAULT_FHIR_PACKAGE))
        .thenReturn(Map.of("l1", "mappedLab"));

    var service = new CodeMappingService(client, properties, cacheFactory, false);
    service.loadConceptMaps();

    // Should still work with the available concept maps
    assertThat(service.mapCode("d1")).isEqualTo("mappedDisease");
    assertThat(service.mapCode("l1")).isEqualTo("mappedLab");
  }

  @Nested
  class Validation {

    @Test
    void shouldRequireBaseUrl() {
      properties.getClient().setBaseUrl(" ");
      assertThatThrownBy(() -> new CodeMappingService(client, properties, cacheFactory, false))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("base URL");
    }

    @Test
    void shouldRequireContextPath() {
      properties.getClient().setContextPath(null);
      assertThatThrownBy(() -> new CodeMappingService(client, properties, cacheFactory, false))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("context path");
    }

    @Test
    void shouldRequireConceptMaps() {
      properties.setConceptMaps(List.of());
      assertThatThrownBy(() -> new CodeMappingService(client, properties, cacheFactory, false))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("concept map");
    }

    @Test
    void shouldRequireCronExpression() {
      properties.setCacheReloadCron(" ");
      assertThatThrownBy(() -> new CodeMappingService(client, properties, cacheFactory, false))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("cron");
    }

    @Test
    void shouldRequirePackageHeadersWhenFhirCoreSplitEnabled() {
      properties.setFhirPackageHeaders(List.of());
      assertThatThrownBy(() -> new CodeMappingService(client, properties, cacheFactory, true))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("FHIR package header");
    }

    @Test
    void shouldNotRequirePackageHeadersWhenFhirCoreSplitDisabled() {
      properties.setFhirPackageHeaders(List.of());
      // Should not throw when fhirCoreSplitEnabled is false
      var service = new CodeMappingService(client, properties, cacheFactory, false);
      assertThat(service).isNotNull();
    }
  }

  @Nested
  class DefaultModeFallback {

    @Nested
    class WithFeignException {

      @Test
      void shouldFallBackToLegacyCallOn403() {
        when(client.getConceptMapWithPackageHeader("DiseaseA", DEFAULT_FHIR_PACKAGE))
            .thenThrow(feignException(403));
        when(client.getConceptMap("DiseaseA")).thenReturn(Map.of("d1", "fallback"));
        when(client.getConceptMapWithPackageHeader("LabA", DEFAULT_FHIR_PACKAGE))
            .thenReturn(Map.of("l1", "mappedLab"));

        var service = new CodeMappingService(client, properties, cacheFactory, false);
        service.loadConceptMaps();

        assertThat(service.mapCode("d1")).isEqualTo("fallback");
        assertThat(service.mapCode("l1")).isEqualTo("mappedLab");
        verify(client).getConceptMap("DiseaseA");
        verify(client, never()).getConceptMap("LabA");
      }

      @Test
      void shouldSkipConceptMapWhenFallbackAlsoFails() {
        when(client.getConceptMapWithPackageHeader("DiseaseA", DEFAULT_FHIR_PACKAGE))
            .thenThrow(feignException(403));
        when(client.getConceptMap("DiseaseA")).thenThrow(new RuntimeException("fallback failed"));
        when(client.getConceptMapWithPackageHeader("LabA", DEFAULT_FHIR_PACKAGE))
            .thenReturn(Map.of("l1", "mappedLab"));

        var service = new CodeMappingService(client, properties, cacheFactory, false);
        service.loadConceptMaps();

        assertThat(service.mapCode("l1")).isEqualTo("mappedLab");
        assertThat(service.mapCode("d1")).isNull();
      }

      @Test
      void shouldNotFallBackOnNon403Errors() {
        when(client.getConceptMapWithPackageHeader("DiseaseA", DEFAULT_FHIR_PACKAGE))
            .thenThrow(feignException(500));
        when(client.getConceptMapWithPackageHeader("LabA", DEFAULT_FHIR_PACKAGE))
            .thenReturn(Map.of("l1", "mappedLab"));

        var service = new CodeMappingService(client, properties, cacheFactory, false);
        service.loadConceptMaps();

        verify(client, never()).getConceptMap("DiseaseA");
        assertThat(service.mapCode("l1")).isEqualTo("mappedLab");
      }

      @Test
      void shouldFallBackForAllConceptMapsIndependently() {
        when(client.getConceptMapWithPackageHeader("DiseaseA", DEFAULT_FHIR_PACKAGE))
            .thenThrow(feignException(403));
        when(client.getConceptMap("DiseaseA")).thenReturn(Map.of("d1", "fallbackDisease"));
        when(client.getConceptMapWithPackageHeader("LabA", DEFAULT_FHIR_PACKAGE))
            .thenThrow(feignException(403));
        when(client.getConceptMap("LabA")).thenReturn(Map.of("l1", "fallbackLab"));

        var service = new CodeMappingService(client, properties, cacheFactory, false);
        service.loadConceptMaps();

        assertThat(service.mapCode("d1")).isEqualTo("fallbackDisease");
        assertThat(service.mapCode("l1")).isEqualTo("fallbackLab");
      }

      private FeignException feignException(final int httpStatus) {
        var request =
            Request.create(
                Request.HttpMethod.GET,
                "http://futs/conceptmap/test",
                Collections.emptyMap(),
                null,
                new RequestTemplate());
        return FeignException.errorStatus(
            "CodeMappingClient#getConceptMapWithPackageHeader(String,String)",
            feign.Response.builder()
                .status(httpStatus)
                .reason("RBAC: access denied")
                .request(request)
                .headers(Collections.emptyMap())
                .build());
      }
    }

    @Nested
    class WithServiceCallException {

      @Test
      void shouldFallBackToLegacyCallOn403() {
        when(client.getConceptMapWithPackageHeader("DiseaseA", DEFAULT_FHIR_PACKAGE))
            .thenThrow(serviceCallException(403));
        when(client.getConceptMap("DiseaseA")).thenReturn(Map.of("d1", "fallback"));
        when(client.getConceptMapWithPackageHeader("LabA", DEFAULT_FHIR_PACKAGE))
            .thenReturn(Map.of("l1", "mappedLab"));

        var service = new CodeMappingService(client, properties, cacheFactory, false);
        service.loadConceptMaps();

        assertThat(service.mapCode("d1")).isEqualTo("fallback");
        assertThat(service.mapCode("l1")).isEqualTo("mappedLab");
        verify(client).getConceptMap("DiseaseA");
        verify(client, never()).getConceptMap("LabA");
      }

      @Test
      void shouldSkipConceptMapWhenFallbackAlsoFails() {
        when(client.getConceptMapWithPackageHeader("DiseaseA", DEFAULT_FHIR_PACKAGE))
            .thenThrow(serviceCallException(403));
        when(client.getConceptMap("DiseaseA")).thenThrow(new RuntimeException("fallback failed"));
        when(client.getConceptMapWithPackageHeader("LabA", DEFAULT_FHIR_PACKAGE))
            .thenReturn(Map.of("l1", "mappedLab"));

        var service = new CodeMappingService(client, properties, cacheFactory, false);
        service.loadConceptMaps();

        assertThat(service.mapCode("l1")).isEqualTo("mappedLab");
        assertThat(service.mapCode("d1")).isNull();
      }

      @Test
      void shouldNotFallBackOnNon403Errors() {
        when(client.getConceptMapWithPackageHeader("DiseaseA", DEFAULT_FHIR_PACKAGE))
            .thenThrow(serviceCallException(500));
        when(client.getConceptMapWithPackageHeader("LabA", DEFAULT_FHIR_PACKAGE))
            .thenReturn(Map.of("l1", "mappedLab"));

        var service = new CodeMappingService(client, properties, cacheFactory, false);
        service.loadConceptMaps();

        verify(client, never()).getConceptMap("DiseaseA");
        assertThat(service.mapCode("l1")).isEqualTo("mappedLab");
      }

      @Test
      void shouldFallBackForAllConceptMapsIndependently() {
        when(client.getConceptMapWithPackageHeader("DiseaseA", DEFAULT_FHIR_PACKAGE))
            .thenThrow(serviceCallException(403));
        when(client.getConceptMap("DiseaseA")).thenReturn(Map.of("d1", "fallbackDisease"));
        when(client.getConceptMapWithPackageHeader("LabA", DEFAULT_FHIR_PACKAGE))
            .thenThrow(serviceCallException(403));
        when(client.getConceptMap("LabA")).thenReturn(Map.of("l1", "fallbackLab"));

        var service = new CodeMappingService(client, properties, cacheFactory, false);
        service.loadConceptMaps();

        assertThat(service.mapCode("d1")).isEqualTo("fallbackDisease");
        assertThat(service.mapCode("l1")).isEqualTo("fallbackLab");
      }

      private ServiceCallException serviceCallException(final int httpStatus) {
        return new ServiceCallException(
            "RBAC: access denied", null, httpStatus, new RuntimeException("cause"));
      }
    }
  }

  @Nested
  class FhirCoreSplit {

    @BeforeEach
    void setUpHeaders() {
      properties.setFhirPackageHeaders(List.of("pkg-a", "pkg-b"));
    }

    @Test
    void shouldLoadMappingsUsingAllPackageHeaders() {
      when(client.getConceptMapWithPackageHeader("DiseaseA", "pkg-a"))
          .thenReturn(Map.of("d1", "mappedA"));
      when(client.getConceptMapWithPackageHeader("DiseaseA", "pkg-b"))
          .thenReturn(Map.of("d2", "mappedB"));
      when(client.getConceptMapWithPackageHeader("LabA", "pkg-a")).thenReturn(Map.of("l1", "labA"));
      when(client.getConceptMapWithPackageHeader("LabA", "pkg-b")).thenReturn(Map.of("l2", "labB"));

      var service = new CodeMappingService(client, properties, cacheFactory, true);
      service.loadConceptMaps();

      assertThat(service.mapCode("d1")).isEqualTo("mappedA");
      assertThat(service.mapCode("d2")).isEqualTo("mappedB");
      assertThat(service.mapCode("l1")).isEqualTo("labA");
      assertThat(service.mapCode("l2")).isEqualTo("labB");
    }

    @Test
    void shouldUseConfiguredHeadersNotDefault() {
      when(client.getConceptMapWithPackageHeader("DiseaseA", "pkg-a"))
          .thenReturn(Map.of("d1", "mapped"));
      when(client.getConceptMapWithPackageHeader("DiseaseA", "pkg-b")).thenReturn(Map.of());
      when(client.getConceptMapWithPackageHeader("LabA", "pkg-a")).thenReturn(Map.of());
      when(client.getConceptMapWithPackageHeader("LabA", "pkg-b")).thenReturn(Map.of());

      var service = new CodeMappingService(client, properties, cacheFactory, true);
      service.loadConceptMaps();

      verify(client, never()).getConceptMapWithPackageHeader("DiseaseA", DEFAULT_FHIR_PACKAGE);
      verify(client, never()).getConceptMapWithPackageHeader("LabA", DEFAULT_FHIR_PACKAGE);
    }

    @Test
    void shouldKeepFirstValueOnDuplicateKeyAcrossHeaders() {
      when(client.getConceptMapWithPackageHeader("DiseaseA", "pkg-a"))
          .thenReturn(Map.of("d1", "first"));
      when(client.getConceptMapWithPackageHeader("DiseaseA", "pkg-b"))
          .thenReturn(Map.of("d1", "second"));
      when(client.getConceptMapWithPackageHeader("LabA", "pkg-a")).thenReturn(Map.of());
      when(client.getConceptMapWithPackageHeader("LabA", "pkg-b")).thenReturn(Map.of());

      var service = new CodeMappingService(client, properties, cacheFactory, true);
      service.loadConceptMaps();

      assertThat(service.mapCode("d1")).isEqualTo("first");
    }

    @Test
    void shouldContinueWithNextHeaderOnFailure() {
      when(client.getConceptMapWithPackageHeader("DiseaseA", "pkg-a"))
          .thenThrow(new RuntimeException("connection refused"));
      when(client.getConceptMapWithPackageHeader("DiseaseA", "pkg-b"))
          .thenReturn(Map.of("d1", "mapped"));
      when(client.getConceptMapWithPackageHeader("LabA", "pkg-a"))
          .thenReturn(Map.of("l1", "labMapped"));
      when(client.getConceptMapWithPackageHeader("LabA", "pkg-b")).thenReturn(Map.of());

      var service = new CodeMappingService(client, properties, cacheFactory, true);
      service.loadConceptMaps();

      assertThat(service.mapCode("d1")).isEqualTo("mapped");
      assertThat(service.mapCode("l1")).isEqualTo("labMapped");
    }

    @Test
    void shouldSkipConceptMapWhenAllHeadersFail() {
      when(client.getConceptMapWithPackageHeader("DiseaseA", "pkg-a"))
          .thenThrow(new RuntimeException("error"));
      when(client.getConceptMapWithPackageHeader("DiseaseA", "pkg-b"))
          .thenThrow(new RuntimeException("error"));
      when(client.getConceptMapWithPackageHeader("LabA", "pkg-a"))
          .thenReturn(Map.of("l1", "labMapped"));
      when(client.getConceptMapWithPackageHeader("LabA", "pkg-b")).thenReturn(Map.of());

      var service = new CodeMappingService(client, properties, cacheFactory, true);
      service.loadConceptMaps();

      assertThat(service.mapCode("l1")).isEqualTo("labMapped");
    }

    @Test
    void shouldThrowWhenAllConceptMapsFailWithAllHeaders() {
      when(client.getConceptMapWithPackageHeader("DiseaseA", "pkg-a"))
          .thenThrow(new RuntimeException("error"));
      when(client.getConceptMapWithPackageHeader("DiseaseA", "pkg-b"))
          .thenThrow(new RuntimeException("error"));
      when(client.getConceptMapWithPackageHeader("LabA", "pkg-a"))
          .thenThrow(new RuntimeException("error"));
      when(client.getConceptMapWithPackageHeader("LabA", "pkg-b"))
          .thenThrow(new RuntimeException("error"));

      var service = new CodeMappingService(client, properties, cacheFactory, true);
      service.loadConceptMaps();

      assertThatThrownBy(() -> service.mapCode("d1"))
          .isInstanceOf(CodeMappingUnavailableException.class)
          .hasMessageContaining("not available");
    }

    @Test
    void shouldMergeResultsFromMultipleHeadersForSameConceptMap() {
      when(client.getConceptMapWithPackageHeader("DiseaseA", "pkg-a"))
          .thenReturn(Map.of("d1", "disease1"));
      when(client.getConceptMapWithPackageHeader("DiseaseA", "pkg-b"))
          .thenReturn(Map.of("d2", "disease2"));
      when(client.getConceptMapWithPackageHeader("LabA", "pkg-a")).thenReturn(Map.of());
      when(client.getConceptMapWithPackageHeader("LabA", "pkg-b")).thenReturn(Map.of());

      var service = new CodeMappingService(client, properties, cacheFactory, true);
      service.loadConceptMaps();

      assertThat(service.mapCode("d1")).isEqualTo("disease1");
      assertThat(service.mapCode("d2")).isEqualTo("disease2");
    }
  }
}
