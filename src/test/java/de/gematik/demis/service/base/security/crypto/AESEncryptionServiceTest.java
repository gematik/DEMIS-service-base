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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class AESEncryptionServiceTest {

  private static final byte[] SECRET = "0123456789abcdef".getBytes(StandardCharsets.UTF_8);
  private static final byte[] ROTATED_SECRET = "fedcba9876543210".getBytes(StandardCharsets.UTF_8);

  private final AESEncryptionService underTest = new AESEncryptionService(SECRET);

  @Test
  void shouldEncryptAndDecrypt() {
    final String plainText = "any sensitive content";

    final byte[] encrypted = underTest.encryptData(plainText);
    final String decrypted = underTest.decryptData(encrypted);

    assertThat(encrypted).isNotEmpty();
    assertThat(decrypted).isEqualTo(plainText);
  }

  @Test
  void shouldEncryptAndDecryptEmptyString() {
    final byte[] encrypted = underTest.encryptData("");
    assertThat(underTest.decryptData(encrypted)).isEmpty();
  }

  @Test
  void shouldUseRandomIvForEveryEncryption() {
    final String plainText = "same text";

    final byte[] firstEncrypted = underTest.encryptData(plainText);
    final byte[] secondEncrypted = underTest.encryptData(plainText);

    assertThat(firstEncrypted).isNotEqualTo(secondEncrypted);
    assertThat(underTest.decryptData(firstEncrypted)).isEqualTo(plainText);
    assertThat(underTest.decryptData(secondEncrypted)).isEqualTo(plainText);
  }

  @Test
  void shouldFailDecryptionWhenCiphertextWasManipulated() {
    final byte[] encrypted = underTest.encryptData("payload");
    encrypted[encrypted.length - 1] ^= 1;

    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> underTest.decryptData(encrypted))
        .withMessage("Encrypted data authentication failed");
  }

  @Test
  void shouldFailDecryptionWhenIvWasManipulated() {
    final byte[] encrypted = underTest.encryptData("payload");
    encrypted[0] ^= 1;

    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> underTest.decryptData(encrypted))
        .withMessage("Encrypted data authentication failed");
  }

  @Test
  void shouldFailDecryptionWithDifferentSecret() {
    final byte[] encrypted = underTest.encryptData("payload");

    final byte[] secondSecret = Arrays.copyOf(SECRET, SECRET.length);
    secondSecret[0] ^= 1;
    final AESEncryptionService underTestWithDifferentSecret =
        new AESEncryptionService(secondSecret);

    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> underTestWithDifferentSecret.decryptData(encrypted))
        .withMessage("Encrypted data authentication failed");
  }

  @Test
  void shouldDecryptWithFallbackSecret() {
    final String plainText = "plain text";

    final AESEncryptionService serviceWithOldSecret = new AESEncryptionService(SECRET);
    final byte[] encryptedWithOldSecret = serviceWithOldSecret.encryptData(plainText);

    final AESEncryptionService rotatedService = new AESEncryptionService(ROTATED_SECRET, SECRET);
    final String decryptedData = rotatedService.decryptData(encryptedWithOldSecret);

    assertThat(decryptedData).isEqualTo(plainText);
  }

  @Test
  void shouldStillFailIfPrimaryAndFallbackSecretDoNotMatch() {
    final AESEncryptionService serviceWithOldSecret = new AESEncryptionService(SECRET);
    final byte[] encryptedWithOldSecret = serviceWithOldSecret.encryptData("payload");

    final AESEncryptionService rotatedService =
        new AESEncryptionService(
            ROTATED_SECRET, "aaaaaaaaaaaaaaaa".getBytes(StandardCharsets.UTF_8));

    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> rotatedService.decryptData(encryptedWithOldSecret))
        .withMessage("Encrypted data authentication failed");
  }

  @Test
  void shouldRejectNullDataOnEncryption() {
    assertThatExceptionOfType(NullPointerException.class)
        .isThrownBy(() -> underTest.encryptData(null))
        .withMessage("data to encrypt must not be null");
  }

  @Test
  void shouldRejectNullDataOnDecryption() {
    assertThatExceptionOfType(NullPointerException.class)
        .isThrownBy(() -> underTest.decryptData(null))
        .withMessage("Encrypted data must not be null");
  }

  @Test
  void shouldRejectTooShortEncryptedDataOnDecryption() {
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> underTest.decryptData(new byte[12]))
        .withMessage("Encrypted data is too short");
  }

  @Test
  void shouldRejectNullSecret() {
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> new AESEncryptionService(null))
        .withMessage("Encryption secret must be set");
  }

  @Test
  void shouldRejectEmptySecret() {
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> new AESEncryptionService(new byte[0]))
        .withMessage("Encryption secret must be set");
  }

  @Test
  void shouldRejectInvalidSecretLength() {
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> new AESEncryptionService(new byte[8]));
  }

  @Test
  void shouldRejectInvalidFallbackSecretLength() {
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> new AESEncryptionService(SECRET, new byte[8]));
  }
}
