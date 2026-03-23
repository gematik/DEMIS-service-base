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

import de.gematik.demis.service.base.error.ServiceCallException;
import feign.FeignException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.StringUtils;

/**
 * Service for managing code mappings.
 *
 * <p>This service interacts with the {@link CodeMappingClient} to load so-called "concept maps"
 * (key-value mappings for codes) from the external Code Mapping Service and exposes them locally
 * via an in-memory cache.
 *
 * <h2>Overall flow / algorithm</h2>
 *
 * <h3>1. Initial instantiation</h3>
 *
 * <ol>
 *   <li>In the constructor the {@link CodeMappingProperties} are validated. This ensures that base
 *       URL, context path, cron expression, and at least one concept map are configured. When the
 *       FHIR core split feature flag ({@code feature.flag.fhir.core.split}) is enabled, at least
 *       one FHIR profile header must also be configured.
 *   <li>The list of all concept maps to load is taken from the properties and stored in {@link
 *       #allConceptMaps}.
 *   <li>If the FHIR core split feature flag is enabled, the list of FHIR profile headers is taken
 *       from the properties and stored in {@link #fhirProfileHeaders}.
 *   <li>Using the {@link ReloadableCacheFactory} a {@link ReloadableCache} instance is created with
 *       a {@link java.util.function.Supplier}. This supplier points to {@link
 *       #loadConceptMaps(java.util.List)} and defines how the cache obtains and refreshes its data.
 *   <li>At this point the cache is still empty – it will be populated <b>lazily</b> on first use or
 *       by the scheduler.
 * </ol>
 *
 * <h3>2. Lazy initial fill on first access</h3>
 *
 * <ol>
 *   <li>On the first invocation of {@link #mapCode(String)} the service checks whether the cache
 *       already contains entries ({@link ReloadableCache#hasEntries()}).
 *   <li>If the cache is empty, {@link #loadConceptMaps()} is invoked, which in turn triggers {@link
 *       ReloadableCache#loadCache()}.
 *   <li>{@code ReloadableCache#loadCache()} calls the supplier registered in the constructor,
 *       namely {@link #loadConceptMaps(java.util.List)} with {@link #allConceptMaps}. This method
 *       queries all configured concept maps using the {@link CodeMappingClient} and merges them
 *       into a single {@link java.util.Map} instance.
 *   <li>Only if the resulting map is not empty it is stored as an immutable copy in the cache. As a
 *       consequence the cache state either remains empty (if nothing could be loaded) or is fully
 *       replaced with the new snapshot.
 * </ol>
 *
 * <h3>3. Building the cache map</h3>
 *
 * <ol>
 *   <li>{@link #loadConceptMaps(java.util.List)} iterates over all configured concept map names.
 *   <li><b>Default mode (FHIR core split disabled):</b> For each concept map the service first
 *       attempts to call {@link CodeMappingClient#getConceptMapWithHeader(String, String)} with the
 *       default header value {@value #DEFAULT_FHIR_PROFILE}. If this call results in an HTTP 403
 *       (e.g. because the Istio routing rules have not yet been updated for the new header), the
 *       service falls back to {@link CodeMappingClient#getConceptMap(String)} which sends no {@code
 *       x-fhir-profile} header. This fallback ensures backward compatibility during the transition
 *       period.
 *   <li><b>FHIR core split mode (feature flag enabled):</b> For each concept map, the service
 *       iterates through all configured {@code x-fhir-profile} header values and calls {@link
 *       CodeMappingClient#getConceptMapWithHeader(String, String)} for each header. Results from
 *       all successful calls are merged into the cache. Only if none of the headers returns data
 *       for a given concept map, an error is logged for that concept map.
 *   <li>All concept maps are merged into a single {@link java.util.LinkedHashMap}. If a duplicate
 *       key is encountered, the first value is kept and a warning is logged. This makes the result
 *       deterministic while still exposing collisions.
 * </ol>
 *
 * <h3>4. Scheduled (periodic) reload</h3>
 *
 * <ol>
 *   <li>Using {@link #loadConceptMapsScheduled()} a periodic reload of the cache is triggered based
 *       on the {@code demis.codemapping.cache-reload-cron} property.
 *   <li>{@link #loadConceptMapsScheduled()} delegates to {@link #loadConceptMaps()}, which
 *       instructs the {@link ReloadableCache} to reload its data.
 *   <li>The reload replaces the previous cache content atomically with the newly loaded map (if it
 *       is not empty). While the reload is in progress, other threads continue to work against the
 *       old snapshot. Afterward all new lookups see the updated state.
 * </ol>
 *
 * <h3>5. Cache access when mapping codes</h3>
 *
 * <ol>
 *   <li>{@link #mapCode(String)} is the central API used to resolve a laboratory/disease code to
 *       its mapped target value.
 *   <li>If the cache is still empty, a reload is attempted once directly before accessing it (see
 *       section 2). If the cache remains empty afterward, a {@link CodeMappingUnavailableException}
 *       is thrown to signal that mappings are currently not available.
 *   <li>With a populated cache the value for the given key is retrieved through {@link
 *       ReloadableCache#getValue(Object)}. If no entry is found, this is logged and {@code null} is
 *       returned, leaving it up to the caller how to handle that case.
 * </ol>
 *
 * <h3>Error handling and robustness</h3>
 *
 * <ul>
 *   <li>Failures while loading individual concept maps do not abort the overall loading process;
 *       only the affected map is skipped.
 *   <li>In default mode, a 403 from the header-based call triggers a transparent fallback to the
 *       legacy call without header. Other errors skip the concept map directly.
 *   <li>When FHIR core split is enabled, a failure with one header for a concept map does not
 *       prevent attempts with the remaining headers. An error for a concept map is only logged when
 *       all configured headers fail.
 *   <li>If loading fails for all concept maps (none of them produced data), the cache stays empty
 *       and a subsequent call to {@link #mapCode(String)} may result in a {@link
 *       CodeMappingUnavailableException}.
 * </ul>
 */
@Slf4j
@ConditionalOnProperty(name = "demis.codemapping.enabled", havingValue = "true")
public class CodeMappingService {

  static final String DEFAULT_FHIR_PROFILE = "fhir-profile-snapshots";

  private final CodeMappingClient codeMappingClient;
  private final List<String> allConceptMaps;
  private final ReloadableCache<String, String> cache;
  private final boolean fhirCoreSplitEnabled;
  private final List<String> fhirProfileHeaders;

  public CodeMappingService(
      final CodeMappingClient codeMappingClient,
      final CodeMappingProperties properties,
      final ReloadableCacheFactory cacheFactory,
      final boolean fhirCoreSplitEnabled) {
    validateProperties(properties, fhirCoreSplitEnabled);
    this.codeMappingClient = Objects.requireNonNull(codeMappingClient, "codeMappingClient");
    this.allConceptMaps = List.copyOf(properties.getConceptMaps());
    this.fhirCoreSplitEnabled = fhirCoreSplitEnabled;
    this.fhirProfileHeaders =
        fhirCoreSplitEnabled ? List.copyOf(properties.getFhirProfileHeaders()) : List.of();
    this.cache = cacheFactory.create("code-mapping", () -> loadConceptMaps(allConceptMaps));
  }

  private void validateProperties(
      final CodeMappingProperties properties, final boolean fhirCoreSplitEnabled) {
    if (!StringUtils.hasText(properties.getClient().getBaseUrl())) {
      throw new IllegalStateException("Code mapping base URL must be configured");
    }
    if (!StringUtils.hasText(properties.getClient().getContextPath())) {
      throw new IllegalStateException("Code mapping context path must be configured");
    }
    if (properties.getConceptMaps().isEmpty()) {
      throw new IllegalStateException("At least one concept map must be configured");
    }
    if (!StringUtils.hasText(properties.getCacheReloadCron())) {
      throw new IllegalStateException("Cache reload cron expression must be configured");
    }
    if (fhirCoreSplitEnabled && properties.getFhirProfileHeaders().isEmpty()) {
      throw new IllegalStateException(
          "At least one FHIR profile header must be configured when FHIR core split is enabled");
    }
  }

  @Scheduled(cron = "${demis.codemapping.cache-reload-cron}")
  void loadConceptMapsScheduled() {
    loadConceptMaps();
  }

  void loadConceptMaps() {
    try {
      cache.loadCache();
    } catch (Exception e) {
      log.error("Failed to load concept maps", e);
    }
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
      if (fhirCoreSplitEnabled) {
        loadConceptMapWithHeaders(concept, merged);
      } else {
        loadConceptMapDefault(concept, merged);
      }
    }
    return merged;
  }

  private static final int HTTP_FORBIDDEN = 403;

  private void loadConceptMapDefault(
      final String concept, final LinkedHashMap<String, String> merged) {
    try {
      final var map = codeMappingClient.getConceptMapWithHeader(concept, DEFAULT_FHIR_PROFILE);
      mergeMap(map, concept, merged);
    } catch (Exception e) {
      if (isForbidden(e)) {
        log.warn(
            "Received 403 for concept map {} with header - falling back to legacy call without"
                + " header",
            concept);
        loadConceptMapLegacy(concept, merged);
      } else {
        log.error("Failed to load concept map: {} - skipping", concept, e);
      }
    }
  }

  /**
   * Checks whether the given exception signals an HTTP 403 Forbidden response. Handles both raw
   * {@link FeignException} (when no custom error decoder is active) and {@link
   * ServiceCallException} (when the {@code FeignErrorDecoder} wraps the original exception).
   */
  private static boolean isForbidden(final Exception e) {
    if (e instanceof FeignException fe) {
      return fe.status() == HTTP_FORBIDDEN;
    }
    if (e instanceof ServiceCallException sce) {
      return sce.getHttpStatus() == HTTP_FORBIDDEN;
    }
    return false;
  }

  private void loadConceptMapLegacy(
      final String concept, final LinkedHashMap<String, String> merged) {
    try {
      final var map = codeMappingClient.getConceptMap(concept);
      mergeMap(map, concept, merged);
    } catch (Exception e) {
      log.error("Failed to load concept map: {} via legacy fallback - skipping", concept, e);
    }
  }

  private void loadConceptMapWithHeaders(
      final String concept, final LinkedHashMap<String, String> merged) {
    boolean anySuccess = false;
    for (final var header : fhirProfileHeaders) {
      try {
        final var map = codeMappingClient.getConceptMapWithHeader(concept, header);
        mergeMap(map, concept, merged);
        anySuccess = true;
      } catch (Exception e) {
        log.warn(
            "Failed to load concept map: {} with header {} - trying next header",
            concept,
            header,
            e);
      }
    }
    if (!anySuccess) {
      log.error(
          "Failed to load concept map: {} with any of the configured headers - skipping", concept);
    }
  }

  private void mergeMap(
      final Map<String, String> map,
      final String concept,
      final LinkedHashMap<String, String> merged) {
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

  public interface ReloadableCacheFactory {
    ReloadableCache<String, String> create(
        String cacheName, Supplier<Map<String, String>> supplier);
  }
}
