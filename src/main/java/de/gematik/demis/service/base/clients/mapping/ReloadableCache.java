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

import java.util.Map;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
class ReloadableCache<T, U> {

  private final String name;
  private final Supplier<Map<T, U>> cacheValuesSupplier;

  @SuppressWarnings({"java:S3077"})
  private volatile Map<T, U> map;

  U getValue(final T key) {
    var currentMap = map;
    if (currentMap == null) {
      loadCache();
      currentMap = map;
    }

    final var result = currentMap != null ? currentMap.get(key) : null;
    if (result == null) {
      log.info(
          "No entry found for key {} in map {}. Map size = {}",
          key,
          name,
          currentMap == null ? "n/a" : currentMap.size());
    }
    return result;
  }

  void loadCache() {
    try {
      final var newMap = cacheValuesSupplier.get();
      if (newMap != null && !newMap.isEmpty()) {
        map = Map.copyOf(newMap);
        log.info("{} conceptmap cache (re)loaded. # entries = {}", name, newMap.size());
      } else if (map == null) {
        log.warn("No entries loaded for concept map {}", name);
      }
    } catch (final RuntimeException e) {
      log.error("Error fetching code map for {}", name, e);
    }
  }

  boolean hasEntries() {
    final var snapshot = map;
    return snapshot != null && !snapshot.isEmpty();
  }

  String getCacheName() {
    return this.name;
  }
}
