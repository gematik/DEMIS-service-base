package de.gematik.demis.service.base.fhir.cleanup;

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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Verifies that {@code failOnError} of the {@link ExtensionCleanupService} is bound to the
 * environment variable {@code FEATURE_FLAG_ERROR_ON_EXTENSION_PROCESSING_ENABLED} via the base
 * configuration.
 */
class ExtensionCleanupFeatureFlagTest {

  private static final String IMPORT_BASE_CONFIG =
      "spring.config.import=classpath:/base-config-application.yaml";

  @Configuration
  @Import(ExtensionCleanupService.class)
  static class TestConfig {}

  @Nested
  @SpringBootTest(
      classes = TestConfig.class,
      properties = {IMPORT_BASE_CONFIG, "FEATURE_FLAG_ERROR_ON_EXTENSION_PROCESSING_ENABLED=true"})
  class FlagEnabled {

    @Autowired ExtensionCleanupService service;

    @Test
    void failOnErrorIsTrue() {
      assertThat(service.isFailOnError()).isTrue();
    }
  }

  @Nested
  @SpringBootTest(
      classes = TestConfig.class,
      properties = {IMPORT_BASE_CONFIG, "FEATURE_FLAG_ERROR_ON_EXTENSION_PROCESSING_ENABLED=false"})
  class FlagDisabled {

    @Autowired ExtensionCleanupService service;

    @Test
    void failOnErrorIsFalse() {
      assertThat(service.isFailOnError()).isFalse();
    }
  }

  @Nested
  @SpringBootTest(
      classes = TestConfig.class,
      properties = {IMPORT_BASE_CONFIG})
  class FlagNotSet {

    @Autowired ExtensionCleanupService service;

    @Test
    void failOnErrorDefaultsToFalse() {
      assertThat(service.isFailOnError()).isFalse();
    }
  }
}
