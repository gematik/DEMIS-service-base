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
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties bound to the {@code demis.codemapping.*} namespace.
 *
 * <p>This class holds the configuration options bound to the {@code demis.codemapping.*} namespace:
 *
 * <ul>
 *   <li>{@code enabled} – whether the Code Mapping Service is active.
 *   <li>{@code cacheReloadCron} – cron expression for periodic cache reloads.
 *   <li>{@code client.baseUrl} / {@code client.contextPath} – connection settings for the remote
 *       service.
 *   <li>{@code conceptMaps} – names of the concept maps to load.
 *   <li>{@code fhirProfileHeaders} – list of {@code x-fhir-profile} header values used when the
 *       FHIR core split feature flag is enabled. the headers are used to reach different futs
 *       instances which offer different snapshots
 * </ul>
 */
@Getter
@ConfigurationProperties(prefix = "demis.codemapping")
public class CodeMappingProperties {

  @Setter private boolean enabled;

  @Setter private String cacheReloadCron;

  private final ClientProperties client = new ClientProperties();

  private final List<String> conceptMaps = new ArrayList<>();

  private final List<String> fhirProfileHeaders = new ArrayList<>();

  // Sets the list of concept maps, clearing any existing entries before adding new ones.
  public void setConceptMaps(final List<String> conceptMaps) {
    this.conceptMaps.clear();
    if (conceptMaps != null) {
      this.conceptMaps.addAll(conceptMaps);
    }
  }

  // Sets the list of FHIR profile headers, clearing any existing entries before adding new ones.
  public void setFhirProfileHeaders(final List<String> fhirProfileHeaders) {
    this.fhirProfileHeaders.clear();
    if (fhirProfileHeaders != null) {
      this.fhirProfileHeaders.addAll(fhirProfileHeaders);
    }
  }

  // Nested class for client-specific properties.
  @Getter
  @Setter
  public static class ClientProperties {
    // Base URL of the Code Mapping Service.
    private String baseUrl;

    // Context path for accessing the Code Mapping Service.
    private String contextPath;
  }
}
