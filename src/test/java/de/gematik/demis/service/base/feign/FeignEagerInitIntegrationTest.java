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
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.demis.service.base.feign.SampleFeignClient.MyRequest;
import de.gematik.demis.service.base.feign.SampleFeignClient.MyResult;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.wiremock.spring.EnableWireMock;

@SpringBootTest(
    classes = FeignEagerInitIntegrationTest.TestApp.class,
    properties = "sample-client.url=http://localhost:${wiremock.server.port}")
@EnableWireMock
@Slf4j
class FeignEagerInitIntegrationTest {

  @Autowired SampleFeignClient client;

  @Test
  void concurrency() throws InterruptedException {
    stubFor(post("/test-with-body").willReturn(okForJson(new MyResult("hello world"))));

    final int threadCount = 3;
    final CountDownLatch startSignal = new CountDownLatch(1);
    final CountDownLatch finishSignal = new CountDownLatch(threadCount);
    final AtomicInteger successCounter = new AtomicInteger(0);

    try (final ExecutorService executorService = Executors.newFixedThreadPool(threadCount)) {
      for (int i = 0; i < threadCount; i++) {
        executorService.submit(
            () -> {
              try {
                startSignal.await();
                log.info("sending request...");
                client.standardRequest(new MyRequest("My Question"));
                successCounter.incrementAndGet();
              } catch (final Exception ex) {
                log.error("Feign Exception: ", ex);
              } finally {
                finishSignal.countDown();
              }
            });
      }
      startSignal.countDown();
      assertThat(finishSignal.await(1, TimeUnit.SECONDS)).isTrue();
    }

    assertThat(successCounter).hasValue(threadCount);
  }

  @SpringBootApplication
  @EnableFeignClients(clients = SampleFeignClient.class)
  static class TestApp {
    @Autowired SampleFeignClient forceSpringToCreateClientInStartup;
  }
}
