package de.gematik.demis.service.base.feign;

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

import de.gematik.demis.service.base.feign.annotations.ErrorCode;
import de.gematik.demis.service.base.feign.annotations.HttpStatusExceptionMapping;
import de.gematik.demis.service.base.feign.annotations.HttpStatusExceptionMappings;
import feign.MethodMetadata;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.cloud.openfeign.support.SpringMvcContract;
import org.springframework.core.convert.ConversionService;

public class FeignContract extends SpringMvcContract {

  private final Map<String, HttpStatusExceptionMapping[]> errorMapping = new HashMap<>();
  private final Map<String, ErrorCode> methodErrorCodeMap = new HashMap<>();

  public FeignContract(ConversionService conversionService) {
    super(Collections.emptyList(), conversionService);
  }

  @Override
  protected void processAnnotationOnMethod(
      final MethodMetadata data, final Annotation methodAnnotation, final Method method) {
    final String configKey = data.configKey();
    addExceptionMapping(configKey, methodAnnotation);
    addErrorCode(configKey, methodAnnotation);

    super.processAnnotationOnMethod(data, methodAnnotation, method);
  }

  private void addErrorCode(final String configKey, final Annotation methodAnnotation) {
    if (methodAnnotation instanceof ErrorCode anno) {
      methodErrorCodeMap.put(configKey, anno);
    }
  }

  private void addExceptionMapping(final String configKey, Annotation methodAnnotation) {
    HttpStatusExceptionMapping[] mapping = null;
    if (methodAnnotation instanceof HttpStatusExceptionMapping anno) {
      mapping = new HttpStatusExceptionMapping[] {anno};
    } else if (methodAnnotation instanceof HttpStatusExceptionMappings anno) {
      mapping = anno.value();
    }
    if (mapping != null) {
      errorMapping.put(configKey, mapping);
    }
  }

  public HttpStatusExceptionMapping[] getStatusExceptionMapping(final String methodKey) {
    return errorMapping.get(methodKey);
  }

  public Optional<ErrorCode> getErrorCode(final String methodKey) {
    return Optional.ofNullable(methodErrorCodeMap.get(methodKey));
  }
}
