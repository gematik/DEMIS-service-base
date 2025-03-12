package de.gematik.demis.service.base.apidoc;

/*-
 * #%L
 * service-base
 * %%
 * Copyright (C) 2025 gematik GmbH
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
 * #L%
 */

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;

class DefaultApiSpecConfigTest {

  @Test
  void customOpenAPI() {
    OpenAPI openApi = new DefaultApiSpecConfig().customOpenAPI("test", "1.0");
    assertThat(openApi).isNotNull();
    assertThat(openApi.getInfo()).isNotNull();
    assertThat(openApi.getInfo().getTitle()).isEqualTo("test");
    assertThat(openApi.getInfo().getVersion()).isEqualTo("1.0");
  }
}
