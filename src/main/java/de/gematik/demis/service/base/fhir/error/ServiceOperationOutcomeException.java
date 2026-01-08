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

import de.gematik.demis.service.base.error.ServiceException;
import lombok.Getter;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.springframework.http.HttpStatus;

@Getter
public class ServiceOperationOutcomeException extends ServiceException
    implements OperationOutcomeCarrier {

  private final OperationOutcome operationOutcome;

  public ServiceOperationOutcomeException(
      final HttpStatus responseStatus, final String errorCode, final String message) {
    super(responseStatus, errorCode, message);
    this.operationOutcome = null;
  }

  public ServiceOperationOutcomeException(
      final HttpStatus responseStatus,
      final String errorCode,
      final String message,
      final Throwable cause) {
    super(responseStatus, errorCode, message, cause);
    this.operationOutcome = null;
  }

  public ServiceOperationOutcomeException(final String errorCode, final String message) {
    super(errorCode, message);
    this.operationOutcome = null;
  }

  public ServiceOperationOutcomeException(
      final String errorCode, final String message, final Throwable cause) {
    super(errorCode, message, cause);
    this.operationOutcome = null;
  }

  public ServiceOperationOutcomeException(
      final String errorCode, final OperationOutcome operationOutcome) {
    super(errorCode, null);
    this.operationOutcome = operationOutcome;
  }

  public ServiceOperationOutcomeException(
      final HttpStatus responseStatus,
      final String errorCode,
      final OperationOutcome operationOutcome) {
    super(responseStatus, errorCode, null);
    this.operationOutcome = operationOutcome;
  }

  public ServiceOperationOutcomeException(
      final HttpStatus responseStatus,
      final String errorCode,
      final String message,
      final OperationOutcome operationOutcome) {
    super(responseStatus, errorCode, message, null);
    this.operationOutcome = operationOutcome;
  }
}
