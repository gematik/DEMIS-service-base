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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
    when(client.getConceptMap("DiseaseA")).thenReturn(Map.of("d1", "mapped"));
    when(client.getConceptMap("LabA")).thenReturn(Map.of());

    var service = new CodeMappingService(client, properties, cacheFactory);
    service.loadConceptMaps();

    assertThat(service.mapCode("d1")).isEqualTo("mapped");
  }

  @Test
  void shouldLoadMappingsAndMapLaboratoryCode() {
    when(client.getConceptMap("DiseaseA")).thenReturn(Map.of());
    when(client.getConceptMap("LabA")).thenReturn(Map.of("l1", "mappedLab"));

    var service = new CodeMappingService(client, properties, cacheFactory);
    service.loadConceptMaps();

    assertThat(service.mapCode("l1")).isEqualTo("mappedLab");
  }

  @Test
  void shouldKeepFirstValueWhenDuplicateKeyAppears() {
    properties.setConceptMaps(List.of("DiseaseA", "DiseaseB"));
    when(client.getConceptMap("DiseaseA")).thenReturn(Map.of("d1", "first"));
    when(client.getConceptMap("DiseaseB")).thenReturn(Map.of("d1", "second"));

    var service = new CodeMappingService(client, properties, cacheFactory);
    service.loadConceptMaps();

    assertThat(service.mapCode("d1")).isEqualTo("first");
  }

  @Test
  void shouldThrowWhenCacheEmpty() {
    when(client.getConceptMap("DiseaseA")).thenReturn(Map.of());
    when(client.getConceptMap("LabA")).thenReturn(Map.of());

    var service = new CodeMappingService(client, properties, cacheFactory);
    service.loadConceptMaps();

    assertThatThrownBy(() -> service.mapCode("d1"))
        .isInstanceOf(CodeMappingUnavailableException.class)
        .hasMessageContaining("not available");
  }

  @Test
  void shouldHandleMissingConceptMapGracefully() {
    properties.setConceptMaps(List.of("DiseaseA", "MissingMap", "LabA"));
    when(client.getConceptMap("DiseaseA")).thenReturn(Map.of("d1", "mappedDisease"));
    when(client.getConceptMap("MissingMap")).thenThrow(new RuntimeException("404 Not Found"));
    when(client.getConceptMap("LabA")).thenReturn(Map.of("l1", "mappedLab"));

    var service = new CodeMappingService(client, properties, cacheFactory);
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
      assertThatThrownBy(() -> new CodeMappingService(client, properties, cacheFactory))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("base URL");
    }

    @Test
    void shouldRequireContextPath() {
      properties.getClient().setContextPath(null);
      assertThatThrownBy(() -> new CodeMappingService(client, properties, cacheFactory))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("context path");
    }

    @Test
    void shouldRequireConceptMaps() {
      properties.setConceptMaps(List.of());
      assertThatThrownBy(() -> new CodeMappingService(client, properties, cacheFactory))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("concept map");
    }

    @Test
    void shouldRequireCronExpression() {
      properties.setCacheReloadCron(" ");
      assertThatThrownBy(() -> new CodeMappingService(client, properties, cacheFactory))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("cron");
    }
  }
}
