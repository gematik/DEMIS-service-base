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
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik,
 * find details in the "Readme" file.
 * #L%
 */

import static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder.okForJson;
import static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder.responseDefinition;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import de.gematik.demis.service.base.error.ServiceCallException;
import de.gematik.demis.service.base.feign.SampleFeignClient.MyResult;
import feign.FeignException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootTest(
    classes = FeignAnnotationIntegrationTest.TestApp.class,
    properties = "sample-client.url=http://localhost:${wiremock.server.port}")
@AutoConfigureWireMock(port = 0)
class FeignAnnotationIntegrationTest {

  @Autowired SampleFeignClient client;

  private static void mockStub(final ResponseDefinitionBuilder responseDefinitionBuilder) {
    WireMock.stubFor(WireMock.post("/test").willReturn(responseDefinitionBuilder));
  }

  @Test
  void success() {
    final var response = new MyResult("hello world");
    mockStub(okForJson(response));
    final MyResult result = client.standardRequest();
    assertThat(result).isEqualTo(response);
  }

  @Test
  void badRequest_noAnnotation() {
    mockStub(responseDefinition().withStatus(400));
    assertThatThrownBy(() -> client.standardRequest())
        .isExactlyInstanceOf(ServiceCallException.class)
        .hasCauseExactlyInstanceOf(FeignException.BadRequest.class)
        .hasFieldOrPropertyWithValue("errorCode", null);
  }

  @Test
  void badRequest_errorCode() {
    mockStub(responseDefinition().withStatus(400));
    assertThatThrownBy(() -> client.withErrorCodeAnnotation())
        .isExactlyInstanceOf(ServiceCallException.class)
        .hasFieldOrPropertyWithValue("errorCode", "rc-005");
  }

  @Test
  void badRequest_exceptionMapping() {
    mockStub(responseDefinition().withStatus(422));
    assertThatThrownBy(() -> client.withExceptionMappingAnnotation())
        .isExactlyInstanceOf(MyException.class);
  }

  @Test
  void badRequest_exceptionMapping_otherHttpCode() {
    mockStub(responseDefinition().withStatus(401));
    assertThatThrownBy(() -> client.standardRequest())
        .isExactlyInstanceOf(ServiceCallException.class)
        .hasCauseExactlyInstanceOf(FeignException.Unauthorized.class);
  }

  @SpringBootApplication
  @EnableFeignClients(clients = SampleFeignClient.class)
  static class TestApp {}
}
