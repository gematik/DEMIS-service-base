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

import static de.gematik.demis.service.base.feign.HeadersForwardingRequestInterceptor.DEFAULT_HEADERS_TO_FORWARD;

import jakarta.annotation.PostConstruct;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.support.SpringMvcContract;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot auto-configuration that registers a {@link HeadersForwardingRequestInterceptor} for
 * Feign clients.
 *
 * <p>When enabled, this interceptor copies configured HTTP headers from the current incoming {@link
 * jakarta.servlet.http.HttpServletRequest} to outgoing Feign requests.
 *
 * <p>If a {@link HeadersForwardingRequestInterceptor} bean is already defined in the application
 * context, this auto-configuration backs off. This allows applications to provide their own
 * interceptor instance or to compose it with other {@link feign.RequestInterceptor}s.
 */
@AutoConfiguration
@EnableConfigurationProperties(FeignHeaderForwardingProperties.class)
@ConditionalOnClass(SpringMvcContract.class)
@ConditionalOnProperty(value = "base.feign.header.forwarding.enabled", havingValue = "true")
@Slf4j
public class FeignHeadersForwardingConfiguration {

  @Bean
  @ConditionalOnMissingBean(HeadersForwardingRequestInterceptor.class)
  public HeadersForwardingRequestInterceptor createGlobalHeaderForwardingRequestInterceptor(
      FeignHeaderForwardingProperties props) {

    Set<String> headers =
        props.getHeaders().isEmpty() ? DEFAULT_HEADERS_TO_FORWARD : props.getHeaders();
    return new HeadersForwardingRequestInterceptor(headers);
  }

  @PostConstruct
  void log() {
    log.info("Feign header forwarding activated!");
  }
}
