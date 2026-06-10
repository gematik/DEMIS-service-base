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

import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.cloud.openfeign.FeignClientFactory;
import org.springframework.cloud.openfeign.support.FeignHttpMessageConverters;

/**
 * Workaround to avoid a feign bug. see <a
 * href="https://github.com/spring-cloud/spring-cloud-openfeign/issues/1307">1307</a> and <a
 * href="https://github.com/spring-cloud/spring-cloud-openfeign/issues/1371">1371</a>.
 * Initialization of converters is not thread safe. There is a race condition if there are two first
 * requests concurrent. To avoid this we eager initialize the converters in the main single thread.
 */
@RequiredArgsConstructor
@Slf4j
public class FeignHttpMessageConverterInitializer implements SmartInitializingSingleton {

  private final FeignClientFactory feignClientFactory;

  @Override
  public void afterSingletonsInstantiated() {
    final Set<String> contextNames = feignClientFactory.getContextNames();
    log.info("init feign message converters for {}", contextNames);
    for (final String contextName : contextNames) {
      final FeignHttpMessageConverters feignHttpMessageConverters =
          feignClientFactory.getInstance(contextName, FeignHttpMessageConverters.class);
      if (feignHttpMessageConverters != null) {
        feignHttpMessageConverters.getConverters();
      }
    }
  }
}
