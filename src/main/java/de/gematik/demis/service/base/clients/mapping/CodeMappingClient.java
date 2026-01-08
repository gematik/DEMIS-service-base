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

import feign.Headers;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/** Client for mapping codes from the demis.codemapping service. */
@FeignClient(name = "codeMappingClient", url = "${demis.codemapping.client.base-url}")
@ConditionalOnProperty(name = "demis.codemapping.enabled", havingValue = "true")
public interface CodeMappingClient {

  /**
   * Retrieves the concept map for the given concept name.
   *
   * @param conceptName the name of the concept
   * @return a map of string key-value pairs representing the concept map
   */
  @GetMapping(
      value = "${demis.codemapping.client.context-path}conceptmap/{name}",
      produces = APPLICATION_JSON_VALUE)
  @Headers("x-fhir-profile: fhir-profile-snapshots")
  Map<String, String> getConceptMap(@PathVariable("name") String conceptName);
}
