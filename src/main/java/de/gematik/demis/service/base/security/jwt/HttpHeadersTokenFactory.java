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
 * For additional notes and disclaimer from gematik and in case of changes by gematik,
 * find details in the "Readme" file.
 * #L%
 */

import java.util.List;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;

@RequiredArgsConstructor
final class HttpHeadersTokenFactory implements Supplier<Token> {

  private final HttpHeaders headers;

  /**
   * Extracts the Authorization header and creates a {@link Token}.
   *
   * @return a {@link Token} object.
   * @throws IllegalArgumentException if the Authorization header is missing or invalid.
   */
  @Override
  public Token get() {
    final String authorization = getAuthorizationHeader();
    return new AuthorizationHeaderTokenFactory(authorization).get();
  }

  /**
   * Retrieves the Authorization header from the HTTP headers.
   *
   * @return the Authorization header value.
   * @throws IllegalArgumentException if the Authorization header is missing or invalid.
   */
  private String getAuthorizationHeader() {
    List<String> values = headers.get("Authorization");
    if (values == null || values.size() != 1) {
      throw new IllegalArgumentException("Exactly one Authorization header expected");
    }
    String authorization = values.getFirst();
    if (!StringUtils.hasText(authorization)) {
      throw new IllegalArgumentException("Authorization header must not be empty");
    }
    return authorization;
  }
}
