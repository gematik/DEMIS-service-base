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

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * Feign client for retrieving concept maps from the Code Mapping Service.
 *
 * <p>Provides two endpoints: a legacy call without routing header and a package-based call using
 * the {@code x-fhir-package} header for FHIR core split routing. In default mode the service tries
 * the header-based call first and falls back to the legacy call on HTTP 403, ensuring compatibility
 * during the Istio routing transition.
 */
@FeignClient(name = "codeMappingClient", url = "${demis.codemapping.client.base-url}")
@ConditionalOnProperty(name = "demis.codemapping.enabled", havingValue = "true")
public interface CodeMappingClient {

  /**
   * Retrieves the concept map without any routing header (legacy mode).
   *
   * @param conceptName the name of the concept map
   * @return key-value pairs representing the concept map
   */
  @GetMapping(
      value = "${demis.codemapping.client.context-path}conceptmap/{name}",
      produces = APPLICATION_JSON_VALUE)
  Map<String, String> getConceptMap(@PathVariable("name") String conceptName);

  /**
   * Retrieves the concept map, routing via the {@code x-fhir-package} header.
   *
   * @param conceptName the name of the concept map
   * @param fhirPackage the value for the {@code x-fhir-package} header
   * @return key-value pairs representing the concept map
   */
  @GetMapping(
      value = "${demis.codemapping.client.context-path}conceptmap/{name}",
      produces = APPLICATION_JSON_VALUE)
  Map<String, String> getConceptMapWithPackageHeader(
      @PathVariable("name") String conceptName,
      @RequestHeader("x-fhir-package") String fhirPackage);
}
