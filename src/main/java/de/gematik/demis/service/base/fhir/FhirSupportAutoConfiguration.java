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

import ca.uhn.fhir.context.FhirContext;
import de.gematik.demis.service.base.fhir.outcome.FhirOperationOutcomeProperties;
import de.gematik.demis.service.base.fhir.outcome.FhirOperationOutcomeService;
import de.gematik.demis.service.base.fhir.response.FhirResponseConverter;
import de.gematik.demis.service.base.fhir.response.FhirResponseConverterProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@ConditionalOnClass(FhirContext.class)
@ConditionalOnProperty(value = "base.fhir.enabled", havingValue = "true", matchIfMissing = false)
@AutoConfiguration
@Import({FhirResponseConverter.class, FhirOperationOutcomeService.class})
@EnableConfigurationProperties({
  FhirOperationOutcomeProperties.class,
  FhirResponseConverterProperties.class
})
@Slf4j
public class FhirSupportAutoConfiguration {

  @ConditionalOnMissingBean(FhirContext.class)
  @Bean
  public FhirContext fhirContext() {
    log.info("creating r4 fhir context");
    return FhirContext.forR4Cached();
  }
}
