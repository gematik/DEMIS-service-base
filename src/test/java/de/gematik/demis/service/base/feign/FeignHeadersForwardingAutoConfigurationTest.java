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
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@ExtendWith(MockitoExtension.class)
class FeignHeadersForwardingAutoConfigurationTest {

  private static final Set<String> UNTOUCHED_HEADERS =
      Set.of("x-test-1", "x-test-2", "x-test-3", "x-test-4");

  private static final Set<String> CUSTOM_HEADERS = Set.of("x-custom-1", "x-custom-2");

  private final RequestTemplate outboundRequest = new RequestTemplate();

  @Mock private HttpServletRequest inboundRequest;

  @AfterEach
  void verifyUntouchedHeaders() {
    assertThat(outboundRequest.headers())
        .doesNotContainKeys(UNTOUCHED_HEADERS.toArray(new String[0]));
  }

  @Test
  void testDefaultsUsedWhenNoCustomHeadersConfigured() {
    FeignHeadersForwardingConfiguration config = new FeignHeadersForwardingConfiguration();
    FeignHeaderForwardingProperties props = new FeignHeaderForwardingProperties();
    props.setHeaders(Set.of());

    for (String header : DEFAULT_HEADERS_TO_FORWARD) {
      when(inboundRequest.getHeader(header)).thenReturn("dummy-value");
    }

    applyFeignRequestTemplate(config, props);

    assertThat(outboundRequest.headers().keySet())
        .containsExactlyInAnyOrderElementsOf(DEFAULT_HEADERS_TO_FORWARD);
  }

  @Test
  void testCustomHeadersOverrideDefaults() {
    FeignHeadersForwardingConfiguration config = new FeignHeadersForwardingConfiguration();

    FeignHeaderForwardingProperties props = new FeignHeaderForwardingProperties();
    props.setHeaders(CUSTOM_HEADERS);

    for (String header : CUSTOM_HEADERS) {
      when(inboundRequest.getHeader(header)).thenReturn("dummy-custom value");
    }

    applyFeignRequestTemplate(config, props);

    assertThat(outboundRequest.headers().keySet())
        .containsExactlyInAnyOrderElementsOf(CUSTOM_HEADERS);
  }

  @Test
  void testCustomHeadersAreNormalizedToLowercase() {
    FeignHeadersForwardingConfiguration config = new FeignHeadersForwardingConfiguration();

    FeignHeaderForwardingProperties props = new FeignHeaderForwardingProperties();
    props.setHeaders(Set.of("X-CUSTOM-MIXED-CASE"));

    when(inboundRequest.getHeader("X-CUSTOM-MIXED-CASE")).thenReturn("dummy-value");
    applyFeignRequestTemplate(config, props);
    assertThat(outboundRequest.headers()).containsKey("x-custom-mixed-case");
  }

  private void applyFeignRequestTemplate(
      FeignHeadersForwardingConfiguration config, FeignHeaderForwardingProperties props) {
    try (MockedStatic<RequestContextHolder> mockedStatic =
        Mockito.mockStatic(RequestContextHolder.class)) {
      mockedStatic
          .when(RequestContextHolder::getRequestAttributes)
          .thenReturn(new ServletRequestAttributes(inboundRequest));

      config.createGlobalHeaderForwardingRequestInterceptor(props).apply(outboundRequest);
    }
  }
}
