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
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.annotation.concurrent.ThreadSafe;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Encrypts and decrypts UTF-8 text with AES-128 in GCM mode.
 *
 * <p>Encrypted payloads are returned as {@code IV || ciphertext} and may be decrypted with an
 * optional fallback key.
 *
 * <p><strong>Thread Safety:</strong> This service is thread-safe and can be used concurrently.
 */
@ThreadSafe
public class AESEncryptionService {

  private static final String AES = "AES";
  private static final String AES_GCM_NO_PADDING = "AES/GCM/NoPadding";
  private static final int AES_128_KEY_LENGTH_BYTES = 16;
  private static final int GCM_IV_LENGTH_BYTES = 12;
  private static final int GCM_TAG_LENGTH_BITS = 128;

  private final SecretKeySpec secretKeySpec;
  private final SecretKeySpec fallbackSecretKeySpec;
  private final SecureRandom secureRandom;

  /**
   * Creates the service with a single key for encryption and decryption.
   *
   * @param secret AES-128 key material (16 bytes)
   * @throws IllegalArgumentException if {@code secret} is null, empty, or not 16 bytes long
   * @throws IllegalStateException if the AES-GCM cipher cannot be initialized
   */
  public AESEncryptionService(final byte[] secret) {
    this(secret, null);
  }

  /**
   * Creates the service with a primary key (used for encryption and decryption) and an optional
   * fallback key for decryption (used if decryption failed with primary secret).
   *
   * @param secret primary AES-128 key material (16 bytes)
   * @param fallbackDecryptionSecret optional fallback AES-128 key material (16 bytes)
   * @throws IllegalArgumentException if a provided key is null, empty, or not 16 bytes long
   * @throws IllegalStateException if the AES-GCM cipher cannot be initialized
   */
  public AESEncryptionService(final byte[] secret, final byte[] fallbackDecryptionSecret) {
    validateSecret(secret);
    secretKeySpec = new SecretKeySpec(secret, AES);
    if (fallbackDecryptionSecret == null || fallbackDecryptionSecret.length == 0) {
      fallbackSecretKeySpec = null;
    } else {
      validateSecret(fallbackDecryptionSecret);
      fallbackSecretKeySpec = new SecretKeySpec(fallbackDecryptionSecret, AES);
    }
    secureRandom = new SecureRandom();
    // fail fast: check that cipher can be used
    createCipher();
  }

  /**
   * Encrypts UTF-8 text and returns {@code IV || ciphertext}.
   *
   * @param data plaintext to encrypt
   * @return encrypted payload prefixed with the generated IV
   * @throws IllegalArgumentException if {@code data} is null
   * @throws IllegalStateException if encryption fails due to cryptographic initialization or
   *     processing errors
   */
  public byte[] encryptData(final String data) {
    requireNonNull(data, "data to encrypt must not be null");
    try {
      final byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
      secureRandom.nextBytes(iv);

      final Cipher cipher = createCipher();
      cipher.init(
          Cipher.ENCRYPT_MODE, secretKeySpec, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));

      final byte[] encrypted = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
      final byte[] result = new byte[iv.length + encrypted.length];
      System.arraycopy(iv, 0, result, 0, iv.length);
      System.arraycopy(encrypted, 0, result, iv.length, encrypted.length);
      return result;
    } catch (final GeneralSecurityException e) {
      throw new IllegalStateException("Error encrypting data", e);
    }
  }

  /**
   * Decrypts payloads produced by {@link #encryptData(String)}.
   *
   * @param encryptedData payload in the format {@code IV || ciphertext}
   * @return decrypted UTF-8 plaintext
   * @throws NullPointerException if {@code encryptedData} is null
   * @throws IllegalArgumentException if {@code encryptedData} is too short or authentication fails
   * @throws IllegalStateException if decryption fails due to cryptographic initialization or
   *     processing errors
   */
  public String decryptData(final byte[] encryptedData) {
    requireNonNull(encryptedData, "Encrypted data must not be null");
    if (encryptedData.length <= GCM_IV_LENGTH_BYTES) {
      throw new IllegalArgumentException("Encrypted data is too short");
    }
    try {
      final byte[] iv = Arrays.copyOfRange(encryptedData, 0, GCM_IV_LENGTH_BYTES);
      final byte[] ciphertext =
          Arrays.copyOfRange(encryptedData, GCM_IV_LENGTH_BYTES, encryptedData.length);
      return decryptWithPrimaryOrFallback(ciphertext, iv);
    } catch (final AEADBadTagException e) {
      throw new IllegalArgumentException("Encrypted data authentication failed", e);
    } catch (final GeneralSecurityException e) {
      throw new IllegalStateException("Error decrypting data", e);
    }
  }

  private String decryptWithPrimaryOrFallback(final byte[] ciphertext, final byte[] iv)
      throws GeneralSecurityException {
    try {
      return decryptWithSecret(ciphertext, iv, secretKeySpec);
    } catch (final AEADBadTagException e) {
      if (fallbackSecretKeySpec == null) {
        throw e;
      }
      return decryptWithSecret(ciphertext, iv, fallbackSecretKeySpec);
    }
  }

  private String decryptWithSecret(
      final byte[] ciphertext, final byte[] iv, final SecretKeySpec keySpec)
      throws GeneralSecurityException {
    final Cipher cipher = createCipher();
    cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
    final byte[] decrypted = cipher.doFinal(ciphertext);
    return new String(decrypted, StandardCharsets.UTF_8);
  }

  private void validateSecret(final byte[] secret) {
    if (secret == null || secret.length == 0) {
      throw new IllegalArgumentException("Encryption secret must be set");
    }
    if (secret.length != AES_128_KEY_LENGTH_BYTES) {
      throw new IllegalArgumentException("Encryption secret must be exactly 16 bytes for AES-128");
    }
  }

  private Cipher createCipher() {
    try {
      return Cipher.getInstance(AES_GCM_NO_PADDING);
    } catch (final GeneralSecurityException e) {
      throw new IllegalStateException("Error initializing AES-GCM cipher", e);
    }
  }
}
