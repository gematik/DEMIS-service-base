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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HexFormat;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.source.InvalidConfigurationPropertyValueException;

@Slf4j
class HashServiceTest {

  private static final HexFormat HEX_FORMAT = HexFormat.of();
  private static final String PEPPER_SECRET = "MmbSvONGwV4J3WkG2Io5CrYTpSl2whWw9gjOodfVw2w=";
  private static final String PEPPER_PROPERTY_KEY = "app.hash.pepper";

  private HashService underTest;

  private HashService createHashService(final String pepperSecret) {
    return new HashService(pepperSecret.getBytes());
  }

  @Nested
  class PepperSecret {
    @Test
    void pepperMustNotBeEmpty() {
      assertThatThrownBy(() -> createHashService(""))
          .isInstanceOf(InvalidConfigurationPropertyValueException.class)
          .hasMessageContaining(PEPPER_PROPERTY_KEY)
          .hasMessageContaining("Pepper secret must be set");
    }

    @Test
    void pepperMustBeAtLeast32Bytes() {
      assertThatThrownBy(() -> createHashService("!!!too short!!!"))
          .isInstanceOf(InvalidConfigurationPropertyValueException.class)
          .hasMessageContaining(PEPPER_PROPERTY_KEY)
          .hasMessageContaining("Pepper secret must be at least 32 bytes");
    }
  }

  @Nested
  class Hashing {

    @BeforeEach
    void setUp() {
      underTest = createHashService(PEPPER_SECRET);
    }

    @ParameterizedTest
    @CsvSource({
      "123e4567-e89b-12d3-a456-426614174000, dba0014f9c0a300ef1be739e6bea13fce61e6a94f0e8c94643bf923b33e7bd90",
      "550e8400-e29b-41d4-a716-446655440000, 6345d8c7ae237cb7235913e7969c7e383005704fe5f2cd202a872637179b5240",
      "f47ac10b-58cc-4372-a567-0e02b2c3d479, ea35a012c0ff3de3ac191a2aafa1335349b6fc22246545c251678ccba8534be8"
    })
    void hashIsHMAC256(final String uuidAsInput, final String expectedHash) {
      final byte[] hash = underTest.hash(uuidAsInput);
      final String hashAsHexString = HEX_FORMAT.formatHex(hash);
      assertThat(hashAsHexString).isEqualTo(expectedHash);
    }

    @Test
    void hashIsDeterministic() {
      final String input = "testInput";
      final byte[] hash1 = underTest.hash(input);
      final byte[] hash2 = underTest.hash(input);

      assertThat(hash1).isEqualTo(hash2).isNotEmpty();
    }

    @Test
    void hashIsDifferentForDifferentInputs() {
      final byte[] hash1 = underTest.hash("input1");
      final byte[] hash2 = underTest.hash("input2");

      assertThat(hash1).isNotEqualTo(hash2).isNotEmpty();
    }

    @Test
    void hashDependsOnPepper() {
      final HashService serviceWithOtherPepper = createHashService(PEPPER_SECRET + "2");

      final String input = "same Input";
      final byte[] hash1 = underTest.hash(input);
      final byte[] hash2 = serviceWithOtherPepper.hash(input);

      assertThat(hash1).isNotEqualTo(hash2).isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          "a",
          "123456",
          "langerTextMitSonderzeichen!@#$%^&*()",
          "input longer than 64 chars - verylonginputverylonginputverylonginputverylonginputverylonginputverylonginputverylonginputverylonginput"
        })
    void hashIsHexAnd64CharsLong(final String input) {
      final byte[] hash = underTest.hash(input);
      assertThat(hash).hasSize(32);
    }

    @Test
    void shouldThrowExceptionOnNullInput() {
      assertThatExceptionOfType(NullPointerException.class)
          .isThrownBy(() -> underTest.hash(null))
          .withMessage("input must not be null");
    }
  }
}
