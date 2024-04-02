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

package de.gematik.demis.service.base.error.rest;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.GATEWAY_TIMEOUT;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.gematik.demis.service.base.error.ServiceCallException;
import de.gematik.demis.service.base.error.ServiceException;
import feign.FeignException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(classes = RestExceptionHandlerIntegrationTest.TestApp.class)
@AutoConfigureMockMvc
class RestExceptionHandlerIntegrationTest {

  private static final String ERROR_ID = "123-abc";
  private static final String TIMESTAMP = "2023-11-06T19:19:21.525781612";
  private static final String URL_GET = "/sample/2";

  @MockBean SampleService service;
  @MockBean ErrorFieldProvider errorFieldProvider;

  @Autowired MockMvc mockMvc;

  @BeforeEach
  void setup() {
    when(errorFieldProvider.generateId()).thenReturn(ERROR_ID);
    when(errorFieldProvider.timestamp()).thenReturn(LocalDateTime.parse(TIMESTAMP));
  }

  @Test
  void validRequest_RuntimeException() throws Exception {
    doThrow(RuntimeException.class).when(service).doSomething();
    validRequest().andExpectAll(matchErrorResponse(INTERNAL_SERVER_ERROR));
  }

  @Test
  void validRequest_ServiceException() throws Exception {
    final HttpStatus status = HttpStatus.UNPROCESSABLE_ENTITY;
    final String errorCode = "56a";
    final String message = "my detail";
    Mockito.doThrow(new ServiceException(status, errorCode, message)).when(service).doSomething();
    validRequest().andExpectAll(matchErrorResponse(status, errorCode, message));
  }

  @Test
  void validRequest_ResponseStatusException() throws Exception {
    final HttpStatus expectedStatus = HttpStatus.I_AM_A_TEAPOT;
    doThrow(new ResponseStatusException(expectedStatus)).when(service).doSomething();
    validRequest().andExpectAll(matchErrorResponse(expectedStatus));
  }

  @Test
  void validRequest_SocketTimeoutException() throws Exception {
    final FeignException feignException = mock(FeignException.class);
    when(feignException.getCause()).thenReturn(new SocketTimeoutException());
    doThrow(feignException).when(service).doSomething();
    validRequest().andExpectAll(matchErrorResponse(GATEWAY_TIMEOUT));
  }

  @Test
  void validRequest_ConnectException() throws Exception {
    final FeignException feignException = mock(FeignException.class);
    when(feignException.getCause()).thenReturn(new ConnectException());
    doThrow(feignException).when(service).doSomething();
    validRequest().andExpectAll(matchErrorResponse(SERVICE_UNAVAILABLE));
  }

  @Test
  void validRequest_ServiceCallException_Client() throws Exception {
    final String errorCode = "56a";
    Mockito.doThrow(new ServiceCallException("blah", errorCode, 400, null))
        .when(service)
        .doSomething();
    validRequest().andExpectAll(matchErrorResponse(INTERNAL_SERVER_ERROR, errorCode, null));
  }

  @Test
  void validRequest_ServiceCallException_Server() throws Exception {
    final String errorCode = "56a";
    doThrow(new ServiceCallException("blah", errorCode, 500, null)).when(service).doSomething();
    validRequest().andExpectAll(matchErrorResponse(BAD_GATEWAY, errorCode, null));
  }

  @Test
  void invalidRequest_notFound() throws Exception {
    mockMvc
        .perform(get("/doesNotExist"))
        .andExpectAll(matchErrorResponse(NOT_FOUND, null, "No static resource doesNotExist."));
  }

  @Test
  void invalidRequest_MissingQueryParam() throws Exception {
    mockMvc
        .perform(get(URL_GET))
        .andExpectAll(
            matchErrorResponse(
                BAD_REQUEST, null, "Required parameter 'myQueryParam' is not present."));
  }

  // Just this test needs dependency spring-boot-starter-validation
  @Test
  void invalidRequest_BeanValidation() throws Exception {
    mockMvc
        .perform(post("/sample").contentType(MediaType.APPLICATION_JSON).content("{\"number\":55}"))
        .andExpectAll(matchErrorResponse(BAD_REQUEST, null, "Invalid request content."));
  }

  @Test
  void invalidRequest_WrongContentType() throws Exception {
    mockMvc
        .perform(post("/sample").contentType(MediaType.TEXT_PLAIN).content("{\"number\":55}"))
        .andExpectAll(
            matchErrorResponse(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                null,
                "Content-Type 'text/plain;charset=UTF-8' is not supported."));
  }

  private ResultActions validRequest() throws Exception {
    return mockMvc.perform(get(URL_GET).queryParam("myQueryParam", "5"));
  }

  private ResultMatcher[] matchErrorResponse(final HttpStatus httpStatus) {
    return matchErrorResponse(httpStatus, null, null);
  }

  private ResultMatcher[] matchErrorResponse(
      final HttpStatus httpStatus, final String errorCode, final String detail) {
    return new ResultMatcher[] {
      status().is(httpStatus.value()),
      jsonPath("$.id").value(ERROR_ID),
      jsonPath("$.status").value(httpStatus.value()),
      jsonPath("$.timestamp").value(TIMESTAMP),
      jsonPath("$.errorCode").value(errorCode),
      jsonPath("$.detail").value(detail),
      jsonPath("$.path").isNotEmpty()
    };
  }

  @SpringBootApplication
  @Import(SampleRestController.class)
  static class TestApp {}
}
