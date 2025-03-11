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
 * #L%
 */

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

@RequiredArgsConstructor
final class AuthorizationHeaderTokenFactory implements Supplier<Token> {

  private static final String BEARER_PREFIX = "Bearer ";
  private static final int TOKEN_PREFIX_LENGTH = BEARER_PREFIX.length();
  private static final int JWT_PARTS_COUNT = 3;
  private static final int PAYLOAD_INDEX = 1;

  private final String authorizationHeader;

  /**
   * Parses the authorization header to extract a JWT token, decodes its payload, and maps it to a
   * {@link Token}.
   *
   * @return a {@link Token} object containing information extracted from the JWT payload.
   * @throws IllegalArgumentException if the authorization header is invalid or the JWT token cannot
   *     be parsed.
   */
  @Override
  public Token get() {
    validateAuthorizationHeader();

    try {
      String encodedToken = authorizationHeader.substring(TOKEN_PREFIX_LENGTH);
      String payload = extractJwtPayload(encodedToken);
      JwtPayload jwtPayload = parseJwtPayload(payload);

      return buildTokenFromPayload(jwtPayload);
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid JWT token", e);
    }
  }

  /**
   * Validates the authorization header to ensure it is non-empty and starts with the "Bearer "
   * prefix.
   *
   * @throws IllegalArgumentException if the authorization header is invalid.
   */
  private void validateAuthorizationHeader() {
    if (!StringUtils.hasText(authorizationHeader)
        || !authorizationHeader.startsWith(BEARER_PREFIX)) {
      throw new IllegalArgumentException("Authorization header must contain a valid Bearer token");
    }
  }

  /**
   * Extracts the payload section of the JWT token.
   *
   * @param encodedToken the encoded JWT token.
   * @return the payload section of the JWT token.
   * @throws IllegalArgumentException if the JWT token does not have exactly three parts.
   */
  private String extractJwtPayload(String encodedToken) {
    String[] parts = encodedToken.split("\\.");
    if (parts.length != JWT_PARTS_COUNT) {
      throw new IllegalArgumentException("JWT token must contain exactly 3 parts");
    }
    return parts[PAYLOAD_INDEX];
  }

  /**
   * Parses the JWT payload into a {@link JwtPayload} object.
   *
   * @param payload the Base64-encoded JWT payload.
   * @return a {@link JwtPayload} object containing the decoded payload data.
   * @throws IOException if the payload cannot be decoded or mapped to a {@link JwtPayload}.
   */
  private JwtPayload parseJwtPayload(String payload) throws IOException {
    byte[] decodedPayload = decodePayload(payload);
    ObjectMapper mapper = createObjectMapper();
    return mapper.readValue(decodedPayload, JwtPayload.class);
  }

  /**
   * Decodes the Base64-encoded JWT payload.
   *
   * @param payload the Base64-encoded payload.
   * @return a byte array containing the decoded payload.
   */
  private byte[] decodePayload(String payload) {
    DecoderProvider decoderProvider = new DecoderProvider();
    return decoderProvider.decoder(payload).decode(payload);
  }

  /**
   * Creates and configures an {@link ObjectMapper} for deserializing the JWT payload.
   *
   * @return a preconfigured {@link ObjectMapper} instance.
   */
  private ObjectMapper createObjectMapper() {
    return new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }

  /**
   * Builds a {@link Token} object using data extracted from the {@link JwtPayload}.
   *
   * @param jwtPayload the payload extracted from the JWT token.
   * @return a {@link Token} object populated with data from the JWT payload.
   */
  private Token buildTokenFromPayload(JwtPayload jwtPayload) {
    return DemisToken.builder()
        .azp(jwtPayload.azp())
        .accountIsTest(jwtPayload.accountIsTest())
        .ik(jwtPayload.ik())
        .preferredUsername(jwtPayload.preferredUsername())
        .roles(jwtPayload.realmAccess().roles())
        .build();
  }
}
