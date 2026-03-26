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

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Feign {@link RequestInterceptor} that forwards selected HTTP headers from the current incoming
 * servlet request to outgoing Feign requests.
 *
 * <p>The interceptor retrieves the current {@link jakarta.servlet.http.HttpServletRequest} via
 * {@link org.springframework.web.context.request.RequestContextHolder} and copies configured
 * headers into the outgoing Feign {@link RequestTemplate}.
 *
 * <p>This is useful in services where request context headers must be propagated to downstream
 * services invoked via Feign clients.
 *
 * <h2>Automatic activation</h2>
 *
 * <p>The interceptor can be registered automatically via {@link
 * FeignHeadersForwardingConfiguration}. This is the recommended approach if you want to apply the
 * interceptor globally to all Feign clients in your application.
 *
 * <p>Enable it by setting:
 *
 * <pre>{@code
 * base.feign.header.forwarding.enabled=true
 * }</pre>
 *
 * <p>The headers to forward can be configured with:
 *
 * <pre>{@code
 * base.feign.header.forwarding.headers[0]=x-custom-header
 * base.feign.header.forwarding.headers[1]=x-another-header
 * }</pre>
 *
 * <p>If no headers are configured, the interceptor forwards {@link #DEFAULT_HEADERS_TO_FORWARD}.
 *
 * <h2>Manual registration</h2>
 *
 * <p>The interceptor can also be created manually and registered as a Feign {@link
 * RequestInterceptor}.This is the recommended approach if you want to apply the interceptor only to
 * specific Feign clients in your application or combine it with other interceptors in a specific
 * order.
 *
 * <p>Example:
 *
 * <pre>{@code
 * @Bean
 * RequestInterceptor forwardingInterceptor() {
 *     return new HeadersForwardingRequestInterceptor(Set.of("x-custom-header"));
 * }
 * }</pre>
 *
 * <h3>Applying the interceptor only to selected Feign clients</h2>
 *
 * <p>Manual registration allows the interceptor to be applied only to specific Feign clients.
 *
 * <p>This can be achieved by defining the interceptor in a dedicated configuration class and
 * referencing that configuration in the {@code @FeignClient}.
 *
 * <p>Example:
 *
 * <pre>{@code
 * @Configuration
 * class MyFeignClientConfiguration {
 *
 *     @Bean
 *     RequestInterceptor forwardingInterceptor() {
 *         return new HeadersForwardingRequestInterceptor(Set.of("x-custom-header"));
 *     }
 * }
 *
 * @FeignClient(
 *     name = "remote-service",
 *     configuration = MyFeignClientConfiguration.class
 * )
 * interface RemoteServiceClient {
 *
 *     @GetMapping("/endpoint")
 *     String call();
 * }
 * }</pre>
 *
 * <p>In this example, the interceptor is applied only to the {@code RemoteServiceClient}, while
 * other Feign clients remain unaffected.
 *
 * <h3>Combining with other interceptors</h2>
 *
 * <p>This interceptor can be composed with other {@link RequestInterceptor}s when a specific
 * execution order is required.
 *
 * <p>Example:
 *
 * <pre>{@code
 * @Bean
 * RequestInterceptor compositeInterceptor() {
 *     RequestInterceptor forwarding =
 *             new HeadersForwardingRequestInterceptor(Set.of("x-custom-1"));
 *
 *     return template -> {
 *         forwarding.apply(template);
 *         template.header("x-added-by-composite-interceptor", "added-value");
 *     };
 * }
 * }</pre>
 */
@Slf4j
public final class HeadersForwardingRequestInterceptor implements RequestInterceptor {

  public static final Set<String> DEFAULT_HEADERS_TO_FORWARD =
      Set.of(
          "x-fhir-api-version",
          "x-fhir-profile",
          "x-fhir-profile-version",
          "x-fhir-package",
          "x-fhir-package-version",
          "x-api-version",
          "x-fhir-api-submission-type");

  private final Set<String> headersToForward;

  private static void writeDebugLog(List<String> copiedHeaders) {
    if (log.isDebugEnabled()) {
      log.debug(
          "Forwarded headers to Feign request: {}",
          copiedHeaders.isEmpty()
              ? "None"
              : copiedHeaders.stream().sorted().collect(Collectors.joining(", ", "[", "]")));
    }
  }

  public HeadersForwardingRequestInterceptor() {
    this(DEFAULT_HEADERS_TO_FORWARD);
  }

  public HeadersForwardingRequestInterceptor(Set<String> headersToForward) {
    this.headersToForward = Set.copyOf(headersToForward);
  }

  @Override
  public void apply(RequestTemplate requestTemplate) {
    final RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
    if (attrs instanceof ServletRequestAttributes servletAttrs) {
      copyHeadersIntoFeignRequest(servletAttrs.getRequest(), requestTemplate);
    } else {
      log.warn("Feign call triggered outside an HTTP request context. Header forwarding skipped.");
    }
  }

  private void copyHeadersIntoFeignRequest(
      HttpServletRequest inboundControllerRequest, RequestTemplate outboundFeignRequest) {

    List<String> copiedHeaders = new ArrayList<>();
    for (String header : headersToForward) {
      String lowerCaseHeader = header.toLowerCase(Locale.ROOT);
      String value = inboundControllerRequest.getHeader(header);
      if (StringUtils.isNotBlank(value)) {
        outboundFeignRequest.header(lowerCaseHeader, value);
        copiedHeaders.add(lowerCaseHeader);
      }
    }
    writeDebugLog(copiedHeaders);
  }
}
