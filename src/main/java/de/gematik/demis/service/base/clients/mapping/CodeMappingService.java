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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.StringUtils;

@Slf4j
@ConditionalOnProperty(name = "demis.codemapping.enabled", havingValue = "true")
public class CodeMappingService {

  private final CodeMappingClient codeMappingClient;
  private final List<String> allConceptMaps;
  private final ReloadableCache<String, String> cache;
  private final RetryFactory retryFactory;

  public CodeMappingService(
      final CodeMappingClient codeMappingClient,
      final CodeMappingProperties properties,
      final ReloadableCacheFactory cacheFactory,
      final RetryFactory retryFactory) {
    validateProperties(properties);
    this.codeMappingClient = Objects.requireNonNull(codeMappingClient, "codeMappingClient");
    this.retryFactory = Objects.requireNonNull(retryFactory, "retryFactory");
    this.allConceptMaps = List.copyOf(mergeAllConceptMapsIntoSingleList(properties));
    this.cache = cacheFactory.create("code-mapping", () -> loadConceptMaps(allConceptMaps));
  }

  private static @NonNull List<String> mergeAllConceptMapsIntoSingleList(
      CodeMappingProperties properties) {
    final var allMaps = new ArrayList<String>();
    allMaps.addAll(properties.getDisease().getConceptMaps());
    allMaps.addAll(properties.getLaboratory().getConceptMaps());
    return allMaps;
  }

  private void validateProperties(final CodeMappingProperties properties) {
    if (!StringUtils.hasText(properties.getClient().getBaseUrl())) {
      throw new IllegalStateException("Code mapping base URL must be configured");
    }
    if (!StringUtils.hasText(properties.getClient().getContextPath())) {
      throw new IllegalStateException("Code mapping context path must be configured");
    }
    if (properties.getDisease().getConceptMaps().isEmpty()) {
      throw new IllegalStateException("At least one disease concept map must be configured");
    }
    if (properties.getLaboratory().getConceptMaps().isEmpty()) {
      throw new IllegalStateException("At least one laboratory concept map must be configured");
    }
    if (!StringUtils.hasText(properties.getCacheReloadCron())) {
      throw new IllegalStateException("Cache reload cron expression must be configured");
    }
  }

  @Scheduled(cron = "${demis.codemapping.cache-reload-cron}")
  void loadConceptMapsScheduled() {
    loadConceptMaps();
  }

  void loadConceptMaps() {
    ExponentialBackoffRetry retry = retryFactory.create();

    retry.executeWithRetry(
        cache.getCacheName(),
        () -> {
          cache.loadCache();
          return cache.hasEntries();
        },
        hasEntries -> !hasEntries);
  }

  public String mapCode(final String diseaseCode) {
    if (!cache.hasEntries()) {
      cache.loadCache();
      if (!cache.hasEntries()) {
        throw new CodeMappingUnavailableException("Code mapping not available");
      }
    }
    return cache.getValue(diseaseCode);
  }

  private Map<String, String> loadConceptMaps(final List<String> conceptMaps) {
    final var merged = new LinkedHashMap<String, String>();
    for (final var concept : conceptMaps) {
      final var map = codeMappingClient.getConceptMap(concept);
      map.forEach(
          (key, value) -> {
            if (merged.containsKey(key)) {
              log.warn(
                  "Duplicate key {} encountered while loading concept map {} - existing value kept",
                  key,
                  concept);
            } else {
              merged.put(key, value);
            }
          });
    }
    return merged;
  }

  public interface ReloadableCacheFactory {
    ReloadableCache<String, String> create(
        String cacheName, Supplier<Map<String, String>> supplier);
  }

  public interface RetryFactory {
    ExponentialBackoffRetry create();
  }
}
