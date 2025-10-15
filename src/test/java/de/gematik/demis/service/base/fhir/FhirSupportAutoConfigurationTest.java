package de.gematik.demis.service.base.fhir;

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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.uhn.fhir.context.FhirContext;
import de.gematik.demis.service.base.fhir.outcome.FhirOperationOutcomeProperties;
import de.gematik.demis.service.base.fhir.outcome.FhirOperationOutcomeService;
import de.gematik.demis.service.base.fhir.response.FhirResponseConverter;
import de.gematik.demis.service.base.fhir.response.FhirResponseConverterProperties;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ComponentScan;

class FhirSupportAutoConfigurationTest {

  @SpringBootApplication
  @ComponentScan(basePackages = "de.gematik.demis.service.base.fhir.test")
  static class TestApp {}

  @Nested
  @SpringBootTest(classes = {FhirSupportAutoConfigurationTest.TestApp.class})
  class DisabledPerDefault {
    @Autowired ApplicationContext applicationContext;

    @Test
    void noFhirSupportBeans() {
      assertThatThrownBy(() -> applicationContext.getBean(FhirContext.class))
          .isInstanceOf(NoSuchBeanDefinitionException.class);
      assertThatThrownBy(() -> applicationContext.getBean(FhirResponseConverter.class))
          .isInstanceOf(NoSuchBeanDefinitionException.class);
      assertThatThrownBy(() -> applicationContext.getBean(FhirOperationOutcomeService.class))
          .isInstanceOf(NoSuchBeanDefinitionException.class);
      assertThatThrownBy(() -> applicationContext.getBean(FhirResponseConverterProperties.class))
          .isInstanceOf(NoSuchBeanDefinitionException.class);
      assertThatThrownBy(() -> applicationContext.getBean(FhirOperationOutcomeProperties.class))
          .isInstanceOf(NoSuchBeanDefinitionException.class);
    }
  }

  @Nested
  @SpringBootTest(
      classes = {FhirSupportAutoConfigurationTest.TestApp.class},
      properties = "base.fhir.enabled=true")
  class FeatureEnabled {
    @Autowired ApplicationContext applicationContext;

    @Test
    void fhirSupportBeansExist() {
      assertThatCode(() -> applicationContext.getBean(FhirContext.class))
          .doesNotThrowAnyException();
      assertThatCode(() -> applicationContext.getBean(FhirResponseConverter.class))
          .doesNotThrowAnyException();
      assertThatCode(() -> applicationContext.getBean(FhirOperationOutcomeService.class))
          .doesNotThrowAnyException();
      assertThatCode(() -> applicationContext.getBean(FhirResponseConverterProperties.class))
          .doesNotThrowAnyException();
      assertThatCode(() -> applicationContext.getBean(FhirOperationOutcomeProperties.class))
          .doesNotThrowAnyException();
    }
  }
}
