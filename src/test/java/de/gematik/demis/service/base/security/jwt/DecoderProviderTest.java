package de.gematik.demis.service.base.security.jwt;

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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class DecoderProviderTest {
  private final DecoderProvider underTest = new DecoderProvider();
  private static final String BASE64 = "dGVzdA==";
  private static final String BASE64URL = "dGV_zd-A";
  private static final String INVALID_INPUT = "dGVzdA?!";

  @Test
  void shouldThrowExceptionWhenInputStringIsEmpty() {
    assertThrows(IllegalArgumentException.class, () -> underTest.decoder(""), "Invalid input");
  }

  @Test
  void shouldReturnBase64DecoderWhenInputStringIsBase64() {
    final Base64.Decoder decoder = underTest.decoder(BASE64);
    assertNotNull(decoder);
  }

  @Test
  void shouldReturnBase64UrlDecoderWhenInputStringIsBase64Url() {
    final Base64.Decoder decoder = underTest.decoder(BASE64URL);
    assertNotNull(decoder);
  }

  @Test
  void shouldThrowExceptionWhenInputStringIsNotBase64OrBase64Url() {
    assertThrows(
        IllegalArgumentException.class,
        () -> underTest.decoder(INVALID_INPUT),
        "Not a Base64 or Base64Url encoded string");
  }
}
