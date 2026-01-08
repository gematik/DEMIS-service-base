package de.gematik.demis.service.base.fhir.error;

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

import de.gematik.demis.service.base.error.rest.ErrorResponseStrategy;
import de.gematik.demis.service.base.error.rest.api.ErrorDTO;
import de.gematik.demis.service.base.fhir.outcome.FhirOperationOutcomeService;
import de.gematik.demis.service.base.fhir.response.FhirResponseConverter;
import lombok.RequiredArgsConstructor;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.hl7.fhir.r4.model.OperationOutcome.OperationOutcomeIssueComponent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.WebRequest;

@Component
@RequiredArgsConstructor
class FhirErrorResponseStrategy implements ErrorResponseStrategy {

  private final FhirOperationOutcomeService fhirOperationOutcomeService;
  private final FhirResponseConverter fhirResponseConverter;

  @Value("${feature.flag.move-error-id-to-diagnostics:false}")
  private boolean featureFlagMoveErrorIdToDiagnostics;

  @Override
  public ResponseEntity<Object> toResponse(
      final ResponseEntity.BodyBuilder responseBuilder,
      final Exception ex,
      final ErrorDTO errorDTO,
      final WebRequest request) {

    final OperationOutcome operationOutcome = getOrCreateOperationOutcome(ex);
    operationOutcome.addIssue(createOperationOutcome(errorDTO));
    fhirOperationOutcomeService.processOutcome(operationOutcome);

    return fhirResponseConverter.buildResponse(responseBuilder, operationOutcome, request);
  }

  private OperationOutcome getOrCreateOperationOutcome(final Exception ex) {
    OperationOutcome operationOutcome =
        ex instanceof OperationOutcomeCarrier operationOutcomeCarrier
            ? operationOutcomeCarrier.getOperationOutcome()
            : null;
    if (operationOutcome == null) {
      operationOutcome = new OperationOutcome();
    }
    return operationOutcome;
  }

  private OperationOutcomeIssueComponent createOperationOutcome(final ErrorDTO errorDTO) {
    final var issue = new OperationOutcomeIssueComponent();
    issue
        .setSeverity(OperationOutcome.IssueSeverity.ERROR)
        .setCode(errorDTO.status() >= 500 ? IssueType.EXCEPTION : IssueType.PROCESSING)
        .setDetails(new CodeableConcept().addCoding(new Coding().setCode(errorDTO.errorCode())));
    if (featureFlagMoveErrorIdToDiagnostics) {
      issue.setDiagnostics(errorDTO.detail() + " (" + errorDTO.id() + ")");
    } else {
      issue.setDiagnostics(errorDTO.detail()).addLocation(errorDTO.id());
    }
    return issue;
  }
}
