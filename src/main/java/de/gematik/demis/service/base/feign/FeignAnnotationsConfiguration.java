/*
 * Copyright [2024], gematik GmbH
 *
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
 */

package de.gematik.demis.service.base.feign;

import feign.codec.ErrorDecoder;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.openfeign.support.SpringMvcContract;
import org.springframework.context.annotation.Bean;
import org.springframework.format.support.FormattingConversionService;

@ConditionalOnClass(SpringMvcContract.class)
@ConditionalOnBean(FormattingConversionService.class)
@ConditionalOnProperty(
    value = "base.feign.extension.enabled",
    havingValue = "true",
    matchIfMissing = true)
@AutoConfiguration
@Slf4j
public class FeignAnnotationsConfiguration {

  @Bean
  public FeignContract feignContract(final FormattingConversionService conversionService) {
    return new FeignContract(conversionService);
  }

  @Bean
  public ErrorDecoder feignErrorDecoder(final FeignContract feignContract) {
    return new FeignErrorDecoder(feignContract);
  }

  @PostConstruct
  void log() {
    log.info("Additional Feign Annotations for error handling activated!");
  }
}
