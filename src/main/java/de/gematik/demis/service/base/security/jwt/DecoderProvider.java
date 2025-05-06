package de.gematik.demis.service.base.security.jwt;

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
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 * #L%
 */

import java.util.Base64;
import java.util.regex.Pattern;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

@NoArgsConstructor
final class DecoderProvider {
  // Patterns for Base64 and Base64Url
  private static final Pattern BASE64_PATTERN = Pattern.compile("^[A-Za-z0-9+/]+={0,2}$");
  private static final Pattern BASE64URL_PATTERN = Pattern.compile("^[A-Za-z0-9_-]+={0,2}$");

  public Base64.Decoder decoder(String input) {
    if (!StringUtils.hasText(input)) {
      throw new IllegalArgumentException("Invalid input");
    }

    // Check for Base64
    if (BASE64_PATTERN.matcher(input).matches()) {
      return Base64.getDecoder();
    }

    // Check for Base64Url
    if (BASE64URL_PATTERN.matcher(input).matches()) {
      return Base64.getUrlDecoder();
    }

    // If it doesn't match either
    throw new IllegalArgumentException("Not a Base64 or Base64Url encoded string");
  }
}
