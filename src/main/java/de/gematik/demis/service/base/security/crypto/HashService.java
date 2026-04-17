package de.gematik.demis.service.base.security.crypto;

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

import static java.util.Objects.requireNonNull;

import java.nio.charset.StandardCharsets;
import javax.annotation.concurrent.ThreadSafe;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.source.InvalidConfigurationPropertyValueException;

/**
 * Service for generating cryptographic hashes using HMAC-SHA256.
 *
 * <p><strong>Security Considerations:</strong>
 *
 * <ul>
 *   <li>Uses a configurable pepper value to ensure application-specific hash isolation
 *   <li>Attention: Changing the pepper would invalidate all existing hashes
 * </ul>
 *
 * <p><strong>Thread Safety:</strong> This service is thread-safe and can be used concurrently.
 */
@ThreadSafe
public class HashService {

  private static final String HMAC_SHA256 = "HmacSHA256";

  private final SecretKeySpec secretKeySpec;

  public HashService(final byte[] pepperSecret) {
    validatePepperSecret(pepperSecret);
    secretKeySpec = new SecretKeySpec(pepperSecret, HMAC_SHA256);
    // check that MAC Algo can be used
    createMac();
  }

  private void validatePepperSecret(final byte[] pepperSecret) {
    if (pepperSecret == null || pepperSecret.length == 0) {
      throw new InvalidConfigurationPropertyValueException(
          "app.hash.pepper", "", "Pepper secret must be set");
    }
    if (pepperSecret.length < 32) {
      throw new InvalidConfigurationPropertyValueException(
          "app.hash.pepper", "", "Pepper secret must be at least 32 bytes");
    }
  }

  private Mac createMac() {
    try {
      final Mac mac = Mac.getInstance(HMAC_SHA256);
      mac.init(secretKeySpec);
      return mac;
    } catch (final Exception e) {
      // could never happen
      throw new IllegalStateException("Error initializing HMAC", e);
    }
  }

  /**
   * Generates a cryptographic hash from the provided input string using HMAC-SHA256.
   *
   * <p><strong>Hash Properties:</strong>
   *
   * <ul>
   *   <li><strong>Deterministic:</strong> Identical strings always generate identical hashes
   *   <li><strong>Collision-resistant:</strong> Different strings produce different hashes
   *   <li><strong>One-way:</strong> Original string cannot be derived from the hash
   *   <li><strong>Avalanche effect:</strong> Small changes in input produce drastically different
   *       output
   * </ul>
   *
   * @param input string (i.e. sender name)
   * @return the HMAC-SHA256 hash as byte array
   * @throws NullPointerException if input is null
   */
  public byte[] hash(final String input) {
    requireNonNull(input, "input must not be null");
    // Note: Mac is not thread-safe
    final Mac mac = createMac();
    return mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
  }
}
