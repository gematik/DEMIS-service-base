package de.gematik.demis.service.base.fhir.outcome;

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

import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Properties to control operation outcome
 *
 * @param profile Profile URL used for OperationOutcome (optional).
 * @param issueFilter Enables filtering of issues in OperationOutcome
 * @param issueThreshold Minimum severity to include as an issue (e.g. information, warning, error)
 * @param sort Enabled sorting of issues corresponding to their severity
 */
@ConfigurationProperties(prefix = "base.fhir.operation-outcome")
@Slf4j
public record FhirOperationOutcomeProperties(
    @Nullable String profile,
    @DefaultValue("true") boolean issueFilter,
    @DefaultValue("warning") OperationOutcome.IssueSeverity issueThreshold,
    @DefaultValue("true") boolean sort) {

  @PostConstruct
  void log() {
    log.info("Service Base - Fhir OperationOutcome Configuration {}", this);
  }
}
