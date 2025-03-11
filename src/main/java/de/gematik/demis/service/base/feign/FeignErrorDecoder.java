package de.gematik.demis.service.base.feign;

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
 * #L%
 */

import de.gematik.demis.service.base.error.ServiceCallException;
import de.gematik.demis.service.base.feign.annotations.ErrorCode;
import feign.Response;
import feign.RetryableException;
import feign.codec.ErrorDecoder;
import jakarta.annotation.Nullable;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
public class FeignErrorDecoder implements ErrorDecoder {

  private final FeignContract contract;

  private final ErrorDecoder defaultErrorDecode = new Default();

  @Override
  public Exception decode(final String methodKey, final Response response) {
    return handleErrorMapping(methodKey, response).orElseGet(() -> handle(methodKey, response));
  }

  private Exception handle(final String methodKey, final Response response) {
    final Exception feignException = defaultErrorDecode.decode(methodKey, response);

    if (feignException instanceof RetryableException) {
      return feignException;
    }

    return new ServiceCallException(
        feignException.getMessage(), getErrorCode(methodKey), response.status(), feignException);
  }

  @Nullable
  private String getErrorCode(final String methodKey) {
    return contract.getErrorCode(methodKey).map(ErrorCode::value).orElse(null);
  }

  private Optional<Exception> handleErrorMapping(final String methodKey, final Response response) {
    return Optional.ofNullable(contract.getStatusExceptionMapping(methodKey))
        .flatMap(
            map ->
                Arrays.stream(map)
                    .filter(mapping -> mapping.status().value() == response.status())
                    .findFirst()
                    .map(mapping -> createException(mapping.exception(), response)));
  }

  private Exception createException(
      final Class<? extends Exception> clazz, final Response response) {
    try {
      return clazz.getDeclaredConstructor().newInstance();
    } catch (InstantiationException
        | IllegalAccessException
        | InvocationTargetException
        | NoSuchMethodException e) {
      log.error(
          "error instantiating declared exception {}. Falling back to default exception.",
          clazz,
          e);
      return null;
    }
  }
}
