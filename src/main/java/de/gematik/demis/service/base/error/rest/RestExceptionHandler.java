package de.gematik.demis.service.base.error.rest;

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

import de.gematik.demis.service.base.error.ErrorCodeSupplier;
import de.gematik.demis.service.base.error.ServiceCallException;
import de.gematik.demis.service.base.error.ServiceException;
import de.gematik.demis.service.base.error.rest.api.ErrorDTO;
import jakarta.annotation.Nullable;
import jakarta.validation.ValidationException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatus.Series;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Global REST exception handler that translates exceptions into standardized {@link
 * de.gematik.demis.service.base.error.rest.api.ErrorDTO} responses.
 *
 * <p>Maps different exception types to appropriate HTTP status codes:
 *
 * <ul>
 *   <li>{@link ServiceException} &rarr; status from exception or 500
 *   <li>{@link ValidationException} &rarr; 400 Bad Request
 *   <li>{@link ServiceCallException} &rarr; 502 Bad Gateway (server error) or 500
 *   <li>{@link UnsupportedOperationException} &rarr; 501 Not Implemented
 *   <li>{@link ConnectException} (as cause) &rarr; 503 Service Unavailable
 *   <li>{@link SocketTimeoutException} (as cause) &rarr; 504 Gateway Timeout
 *   <li>Other exceptions &rarr; 500 Internal Server Error
 * </ul>
 */
@RestControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class RestExceptionHandler extends ResponseEntityExceptionHandler {

  private final ErrorFieldProvider errorFieldProvider;
  private final SenderProperties senderProperties;
  private final Optional<ErrorResponseStrategy> errorResponseStrategy;
  private final Optional<ErrorCounter> errorCounter;

  private static boolean hasCause(final Exception ex, final Class<? extends Throwable> clazz) {
    Throwable cause = ex;
    while ((cause = cause.getCause()) != null && cause != ex) {
      if (clazz.isInstance(cause)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Handles generic exceptions as fallback. Inspects the cause chain to determine a more specific
   * HTTP status code (503 for {@link ConnectException}, 504 for {@link SocketTimeoutException}).
   * Defaults to 500 Internal Server Error.
   */
  @ExceptionHandler(Exception.class)
  public final ResponseEntity<Object> handleServerException(
      final Exception ex, final WebRequest request) {
    final HttpStatus responseStatus;
    if (hasCause(ex, ConnectException.class)) {
      responseStatus = HttpStatus.SERVICE_UNAVAILABLE;
    } else if (hasCause(ex, SocketTimeoutException.class)) {
      responseStatus = HttpStatus.GATEWAY_TIMEOUT;
    } else {
      responseStatus = HttpStatus.INTERNAL_SERVER_ERROR;
    }
    return handleError(responseStatus, ex, request, null);
  }

  /**
   * Handles {@link ServiceException}s using the HTTP status provided by the exception. Falls back
   * to 500 Internal Server Error if no status is specified.
   */
  @ExceptionHandler(ServiceException.class)
  public final ResponseEntity<Object> handleServiceException(
      final ServiceException ex, final WebRequest request) {
    final HttpStatus responseStatus =
        Optional.ofNullable(ex.getResponseStatus()).orElse(HttpStatus.INTERNAL_SERVER_ERROR);
    return handleError(responseStatus, ex, request, ex.getMessage());
  }

  /** Handles {@link ValidationException}s as 400 Bad Request, indicating invalid client input. */
  @ExceptionHandler(ValidationException.class)
  public final ResponseEntity<Object> handleClientException(
      final Exception ex, final WebRequest request) {
    return handleError(HttpStatus.BAD_REQUEST, ex, request, ex.getMessage());
  }

  /**
   * Handles {@link ServiceCallException}s from downstream service calls. Returns 502 Bad Gateway
   * for server-side errors from the downstream service, or 500 Internal Server Error otherwise.
   */
  @ExceptionHandler(ServiceCallException.class)
  public final ResponseEntity<Object> handleFeignException(
      final ServiceCallException ex, final WebRequest request) {
    final HttpStatus responseStatus =
        Series.resolve(ex.getHttpStatus()) == Series.SERVER_ERROR
            ? HttpStatus.BAD_GATEWAY
            : HttpStatus.INTERNAL_SERVER_ERROR;
    return handleError(responseStatus, ex, request, null);
  }

  /**
   * Handles {@link UnsupportedOperationException}s as 501 Not Implemented. Use this to signal that
   * a requested feature or operation is not yet active or supported.
   */
  @ExceptionHandler(UnsupportedOperationException.class)
  public final ResponseEntity<Object> handleUnsupportedOperationException(
      final UnsupportedOperationException ex, final WebRequest request) {
    return handleError(HttpStatus.NOT_IMPLEMENTED, ex, request, ex.getMessage());
  }

  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      final Exception ex,
      Object body,
      final HttpHeaders headers,
      final HttpStatusCode statusCode,
      final WebRequest request) {
    if (body == null && ex instanceof ErrorResponse errorResponse) {
      body = errorResponse.updateAndGetBody(getMessageSource(), LocaleContextHolder.getLocale());
    }
    final String detail =
        body instanceof ProblemDetail problemDetail ? problemDetail.getDetail() : null;
    return handleError(statusCode, ex, request, detail);
  }

  private ResponseEntity<Object> handleError(
      final HttpStatusCode statusCode,
      final Exception ex,
      final WebRequest request,
      final String detail) {
    final String errorCode =
        ex instanceof ErrorCodeSupplier errorCodeSupplier ? errorCodeSupplier.getErrorCode() : null;
    final ErrorDTO errorDTO = createErrorDTO(statusCode, request, errorCode, detail);
    final String sender = request.getHeader(senderProperties.headerKey());
    logException(statusCode, ex, errorDTO, sender);
    callErrorCounter(errorDTO, sender);
    return toResponseEntity(statusCode, request, ex, errorDTO);
  }

  private ResponseEntity<Object> toResponseEntity(
      final HttpStatusCode statusCode,
      final WebRequest request,
      final Exception ex,
      final ErrorDTO errorDTO) {
    final ResponseEntity.BodyBuilder responseBodeBuilder = ResponseEntity.status(statusCode);
    return errorResponseStrategy
        .map(strategy -> strategy.toResponse(responseBodeBuilder, ex, errorDTO, request))
        .orElseGet(() -> responseBodeBuilder.body(errorDTO));
  }

  private void logException(
      final HttpStatusCode statusCode,
      final Exception ex,
      final ErrorDTO errorDTO,
      final String sender) {
    final String senderLogString = senderProperties.log() ? (" from sender " + sender) : "";
    if (statusCode.is5xxServerError()) {
      log.error("server error processing request: {}{}", errorDTO, senderLogString, ex);
    } else {
      log.info("invalid client request: {}{} -> {}", errorDTO, senderLogString, String.valueOf(ex));
    }
  }

  private void callErrorCounter(final ErrorDTO errorDTO, final String sender) {
    if (errorCounter.isPresent()) {
      try {
        errorCounter.get().errorOccurred(errorDTO, sender);
      } catch (final RuntimeException ex) {
        log.error("Ignore error in error counter", ex);
      }
    }
  }

  private ErrorDTO createErrorDTO(
      final HttpStatusCode statusCode,
      final WebRequest request,
      @Nullable final String errorCode,
      @Nullable final String detail) {
    final String uri =
        request instanceof ServletWebRequest servletWebRequest
            ? servletWebRequest.getRequest().getRequestURI()
            : null;
    return new ErrorDTO(
        errorFieldProvider.generateId(),
        statusCode.value(),
        errorFieldProvider.timestamp(),
        errorCode,
        detail,
        uri);
  }
}
