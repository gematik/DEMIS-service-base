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

import static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder.okForJson;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.client.WireMock;
import de.gematik.demis.service.base.feign.SampleFeignClient.MyResult;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;

@SpringBootTest(
    classes = FeignMicrometerIntegrationTest.TestApp.class,
    properties = {
      "sample-client.url=http://localhost:${wiremock.server.port}",
      "spring.config.import=classpath:/base-config-application.yaml"
    })
@AutoConfigureWireMock(port = 0)
@AutoConfigureObservability
@Slf4j
class FeignMicrometerIntegrationTest {

  @Autowired SampleFeignClient client;

  @Autowired MeterRegistry meterRegistry;

  @BeforeEach
  void setupWireMock() {
    WireMock.stubFor(WireMock.post("/test").willReturn(okForJson(new MyResult("does not matter"))));
  }

  private static boolean hasTag(final Meter.Id id, final String key, final String value) {
    return id.getTags().stream()
        .anyMatch(tag -> key.equals(tag.getKey()) && value.equals(tag.getValue()));
  }

  @Test
  void httpHeaders() {
    client.standardRequest();
    WireMock.verify(
        postRequestedFor(urlEqualTo("/test"))
            .withHeader("X-B3-Sampled", matching("\\d+"))
            .withHeader("X-B3-SpanId", matching(".+"))
            .withHeader("X-B3-TraceId", matching(".+")));
  }

  @Test
  void meters() {
    client.standardRequest();
    final List<Meter.Id> list =
        meterRegistry.getMeters().stream()
            .map(Meter::getId)
            .filter(
                id ->
                    hasTag(id, "client", SampleFeignClient.class.getName())
                        && hasTag(id, "method", "standardRequest"))
            .toList();
    log.info("meters : {}", list);
    assertThat(list).isNotEmpty();
  }

  @SpringBootApplication
  @EnableFeignClients(clients = SampleFeignClient.class)
  static class TestApp {
    @Bean
    MeterRegistry meterRegistry() {
      return new SimpleMeterRegistry();
    }
  }
}
