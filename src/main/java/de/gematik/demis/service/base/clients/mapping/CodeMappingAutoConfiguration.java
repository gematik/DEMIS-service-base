package de.gematik.demis.service.base.clients.mapping;

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

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.FeignClientFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@AutoConfiguration
@EnableConfigurationProperties(CodeMappingProperties.class)
@ConditionalOnClass(FeignClientFactory.class)
@ConditionalOnProperty(prefix = "demis.codemapping", name = "enabled", havingValue = "true")
@EnableScheduling
public class CodeMappingAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  CodeMappingService.RetryFactory retryFactory(final CodeMappingProperties properties) {
    return () ->
        new ExponentialBackoffRetry(
            properties.getRetry().getInitialDelay(),
            properties.getRetry().getMaxDelay(),
            properties.getRetry().getMaxAttempts());
  }

  @Bean
  @ConditionalOnMissingBean
  CodeMappingService codeMappingService(
      final CodeMappingClient codeMappingClient,
      final CodeMappingProperties properties,
      final CodeMappingService.RetryFactory retryFactory) {
    return new CodeMappingService(
        codeMappingClient, properties, ReloadableCache::new, retryFactory);
  }
}
