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

import static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder.okForJson;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static de.gematik.demis.service.base.feign.HeadersForwardingRequestInterceptor.DEFAULT_HEADERS_TO_FORWARD;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import feign.RequestInterceptor;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

class FeignHeaderForwardingIntegrationTest {

  @SpringBootTest(
      webEnvironment = SpringBootTest.WebEnvironment.MOCK,
      classes = FeignHeaderForwardingEnabledWithDefaultHeaders.TestApp.class,
      properties = {
        "sample-client.url=http://localhost:${wiremock.server.port}",
        "base.feign.header.forwarding.enabled=true"
      })
  @AutoConfigureWireMock(port = 0)
  @AutoConfigureMockMvc
  @Nested
  class FeignHeaderForwardingEnabledWithDefaultHeaders {

    @Autowired MockMvc mockMvc;

    @BeforeEach
    void stubRemote() {
      WireMock.stubFor(
          WireMock.post("/test").willReturn(okForJson(new SampleFeignClient.MyResult("ok"))));
    }

    @Test
    void forwardsDefaultHeaderAndDoesNotForwardRandomHeader() throws Exception {
      MockHttpServletRequestBuilder request =
          post("/do-something").header("x-not-forwarded", "nope");

      getAnyDefaultHeaderToForward().ifPresent(header -> request.header(header, "abc"));

      mockMvc.perform(request).andExpect(status().isOk());

      getAnyDefaultHeaderToForward()
          .ifPresent(
              header -> WireMock.verify(requestToRemote().withHeader(header, equalTo("abc"))));

      WireMock.verify(requestToRemote().withoutHeader("x-not-forwarded"));
    }

    @Test
    void doesNotForwardRandomHeader() throws Exception {
      mockMvc
          .perform(post("/do-something").header("x-not-for-forwarding", "nope"))
          .andExpect(status().isOk());

      WireMock.verify(requestToRemote().withoutHeader("x-not-for-forwarding"));
    }

    @SpringBootApplication
    @EnableFeignClients(clients = SampleFeignClient.class)
    static class TestApp {

      @RestController
      @RequiredArgsConstructor
      static class FlowController {
        private final SampleFeignClient feign;

        @PostMapping("/do-something")
        SampleFeignClient.MyResult callFeign() {
          return feign.standardRequest();
        }
      }
    }
  }

  @SpringBootTest(
      webEnvironment = SpringBootTest.WebEnvironment.MOCK,
      classes = FeignHeaderForwardingEnabledWithCustomHeaders.TestApp.class,
      properties = {
        "sample-client.url=http://localhost:${wiremock.server.port}",
        "base.feign.header.forwarding.enabled=true",
        "base.feign.header.forwarding.headers[0]=x-custom-1",
        "base.feign.header.forwarding.headers[1]=x-custom-2"
      })
  @AutoConfigureWireMock(port = 0)
  @AutoConfigureMockMvc
  @Nested
  class FeignHeaderForwardingEnabledWithCustomHeaders {

    @Autowired MockMvc mockMvc;

    @BeforeEach
    void stubRemote() {
      WireMock.stubFor(
          WireMock.post("/test").willReturn(okForJson(new SampleFeignClient.MyResult("ok"))));
    }

    @Test
    void forwardsOnlyCustomHeadersAndNotDefaultOrRandomHeaders() throws Exception {
      MockHttpServletRequestBuilder request =
          post("/do-something")
              .header("x-custom-1", "c1")
              .header("x-custom-2", "c2")
              .header("x-not-forwarded", "nope");

      getAnyDefaultHeaderToForward().ifPresent(header -> request.header(header, "abc"));

      mockMvc.perform(request).andExpect(status().isOk());

      WireMock.verify(
          requestToRemote()
              .withHeader("x-custom-1", equalTo("c1"))
              .withHeader("x-custom-2", equalTo("c2")));

      getAnyDefaultHeaderToForward()
          .ifPresent(header -> WireMock.verify(requestToRemote().withoutHeader(header)));

      WireMock.verify(requestToRemote().withoutHeader("x-not-forwarded"));
    }

    @SpringBootApplication
    @EnableFeignClients(clients = SampleFeignClient.class)
    static class TestApp {

      @RestController
      @RequiredArgsConstructor
      static class FlowController {
        private final SampleFeignClient feign;

        @PostMapping("/do-something")
        SampleFeignClient.MyResult callFeign() {
          return feign.standardRequest();
        }
      }
    }
  }

  @SpringBootTest(
      webEnvironment = SpringBootTest.WebEnvironment.MOCK,
      classes = FeignHeaderForwardingDisabled.TestApp.class,
      properties = {
        "sample-client.url=http://localhost:${wiremock.server.port}",
        "base.feign.header.forwarding.enabled=false"
      })
  @AutoConfigureWireMock(port = 0)
  @AutoConfigureMockMvc
  @Nested
  class FeignHeaderForwardingDisabled {

    @Autowired MockMvc mockMvc;

    @BeforeEach
    void stubRemote() {
      WireMock.stubFor(
          WireMock.post("/test").willReturn(okForJson(new SampleFeignClient.MyResult("ok"))));
    }

    @Test
    void doesNotForwardAnyHeadersWhenDisabled() throws Exception {
      MockHttpServletRequestBuilder request =
          post("/do-something").header("x-not-forwarded", "nope");

      getAnyDefaultHeaderToForward().ifPresent(header -> request.header(header, "abc"));

      mockMvc.perform(request).andExpect(status().isOk());

      getAnyDefaultHeaderToForward()
          .ifPresent(header -> WireMock.verify(requestToRemote().withoutHeader(header)));

      WireMock.verify(requestToRemote().withoutHeader("x-not-forwarded"));
    }

    @SpringBootApplication
    @EnableFeignClients(clients = SampleFeignClient.class)
    static class TestApp {

      @RestController
      @RequiredArgsConstructor
      static class FlowController {
        private final SampleFeignClient feign;

        @PostMapping("/do-something")
        SampleFeignClient.MyResult callFeign() {
          return feign.standardRequest();
        }
      }
    }
  }

  /**
   * We make sure that feign calls outside an HTTP context (i.e. scheduler, async processing) are
   * not affected by the header forwarding interceptor and do not contain any headers.
   */
  @SpringBootTest(
      webEnvironment = SpringBootTest.WebEnvironment.NONE,
      classes = HeaderForwardingInNonHttpContextTest.TestApp.class,
      properties = {
        "sample-client.url=http://localhost:${wiremock.server.port}",
        "base.feign.header.forwarding.enabled=true",
        "base.feign.header.forwarding.headers[0]=x-custom-1",
      })
  @AutoConfigureWireMock(port = 0)
  @Nested
  class HeaderForwardingInNonHttpContextTest {

    @Autowired TestApp.DummyService dummyService;

    @BeforeEach
    void stubRemote() {
      WireMock.stubFor(
          WireMock.post("/test").willReturn(okForJson(new SampleFeignClient.MyResult("ok"))));
    }

    @Test
    void doesNotForwardHeadersOutsideHttpContext() {
      dummyService.doSomethingThatTriggersFeign();

      WireMock.verify(requestToRemote().withoutHeader("x-custom-1"));
    }

    @SpringBootApplication
    @EnableFeignClients(clients = SampleFeignClient.class)
    static class TestApp {

      @RestController
      @RequiredArgsConstructor
      static class FlowController {
        private final DummyService dummyService;

        @PostMapping("/do-something")
        SampleFeignClient.MyResult callFeign() {
          return dummyService.doSomethingThatTriggersFeign();
        }
      }

      @Service
      @RequiredArgsConstructor
      static class DummyService {

        private final SampleFeignClient feign;

        SampleFeignClient.MyResult doSomethingThatTriggersFeign() {
          return feign.standardRequest();
        }
      }
    }
  }

  @SpringBootTest(
      webEnvironment = SpringBootTest.WebEnvironment.MOCK,
      classes = {
        FeignHeaderForwardingWithAdditionalInterceptor.TestApp.class,
        FeignHeaderForwardingWithAdditionalInterceptor.AdditionalInterceptorConfig.class
      },
      properties = {
        "sample-client.url=http://localhost:${wiremock.server.port}",
        "base.feign.header.forwarding.enabled=false"
      })
  @AutoConfigureWireMock(port = 0)
  @AutoConfigureMockMvc
  @Nested
  class FeignHeaderForwardingWithAdditionalInterceptor {

    @Autowired MockMvc mockMvc;

    @BeforeEach
    void stubRemote() {
      WireMock.stubFor(
          WireMock.post("/test").willReturn(okForJson(new SampleFeignClient.MyResult("ok"))));
    }

    @Test
    void forwardsConfiguredHeaderAndAppliesAdditionalInterceptor() throws Exception {
      mockMvc
          .perform(
              post("/do-something")
                  .header("x-custom-1", "forward-me")
                  .header("x-not-forwarded", "nope"))
          .andExpect(status().isOk());

      WireMock.verify(
          requestToRemote()
              .withHeader("x-custom-1", equalTo("forward-me"))
              .withoutHeader("x-not-forwarded")
              .withHeader("x-added-after-forwarding", equalTo("added-value1"))
              .withHeader("x-added-by-custom-interceptor", equalTo("added-value2")));
    }

    @SpringBootApplication
    @EnableFeignClients(clients = SampleFeignClient.class)
    static class TestApp {

      @RestController
      @RequiredArgsConstructor
      static class FlowController {
        private final SampleFeignClient feign;

        @PostMapping("/do-something")
        SampleFeignClient.MyResult callFeign() {
          return feign.standardRequest();
        }
      }
    }

    @TestConfiguration
    static class AdditionalInterceptorConfig {

      @Bean
      RequestInterceptor compositeInterceptor() {
        RequestInterceptor forwarding =
            new HeadersForwardingRequestInterceptor(Set.of("x-custom-1"));

        RequestInterceptor additional =
            template -> {
              if (template.headers().containsKey("x-custom-1")) {
                template.header("x-added-after-forwarding", "added-value1");
              }
              template.header("x-added-by-custom-interceptor", "added-value2");
            };

        return compose(forwarding, additional);
      }

      private static RequestInterceptor compose(RequestInterceptor... interceptors) {
        return template -> {
          for (RequestInterceptor interceptor : interceptors) {
            interceptor.apply(template);
          }
        };
      }
    }
  }

  @SpringBootTest(
      webEnvironment = SpringBootTest.WebEnvironment.MOCK,
      classes = {FeignHeaderForwardingForSelectedClientTest.TestApp.class},
      properties = {
        "sample-client.url=http://localhost:${wiremock.server.port}",
        "base.feign.forwarding.enabled=false"
      })
  @AutoConfigureWireMock(port = 0)
  @AutoConfigureMockMvc
  @Nested
  class FeignHeaderForwardingForSelectedClientTest {

    @Autowired MockMvc mockMvc;

    @BeforeEach
    void stubRemote() {
      WireMock.stubFor(
          WireMock.post("/test").willReturn(okForJson(new SampleFeignClient.MyResult("plain-ok"))));

      WireMock.stubFor(
          WireMock.post("/test-forwarded")
              .willReturn(okForJson(new SampleFeignClient.MyResult("forwarded-ok"))));
    }

    @Test
    void appliesHeaderForwardingOnlyToConfiguredFeignClient() throws Exception {
      mockMvc
          .perform(
              post("/call-both")
                  .header("x-custom-1", "forward-me")
                  .header("x-not-forwarded", "nope"))
          .andExpect(status().isOk());

      WireMock.verify(
          postRequestedFor(urlEqualTo("/test-forwarded"))
              .withHeader("x-custom-1", equalTo("forward-me"))
              .withoutHeader("x-not-forwarded"));

      WireMock.verify(
          postRequestedFor(urlEqualTo("/test"))
              .withoutHeader("x-custom-1")
              .withoutHeader("x-not-forwarded"));
    }

    @SpringBootApplication
    @EnableFeignClients(clients = {SampleFeignClient.class, ForwardingFeignClient.class})
    static class TestApp {

      @RestController
      @RequiredArgsConstructor
      static class FlowController {

        private final SampleFeignClient sampleFeignClient;
        private final ForwardingFeignClient forwardingFeignClient;

        @PostMapping("/call-both")
        Map<String, Object> callBoth() {
          var plain = sampleFeignClient.standardRequest();
          var forwarded = forwardingFeignClient.forwardedRequest();

          return Map.of(
              "plain", plain,
              "forwarded", forwarded);
        }
      }
    }

    @FeignClient(
        name = "forwardingFeignClient",
        url = "${sample-client.url}",
        configuration = ForwardingFeignClientConfig.class)
    interface ForwardingFeignClient {

      @PostMapping("/test-forwarded")
      SampleFeignClient.MyResult forwardedRequest();
    }

    @TestConfiguration
    static class ForwardingFeignClientConfig {

      @Bean
      RequestInterceptor forwardingInterceptor() {
        return new HeadersForwardingRequestInterceptor(Set.of("x-custom-1"));
      }
    }
  }

  private static RequestPatternBuilder requestToRemote() {
    return postRequestedFor(urlEqualTo("/test"));
  }

  private static Optional<String> getAnyDefaultHeaderToForward() {
    return DEFAULT_HEADERS_TO_FORWARD.stream().findAny();
  }
}
