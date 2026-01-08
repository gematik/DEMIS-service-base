package de.gematik.demis.service.base.fhir.error;

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

import static de.gematik.demis.service.base.fhir.error.FhirRestExceptionHandlerIntegrationTest.PROFILE;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import de.gematik.demis.fhirparserlibrary.MessageType;
import de.gematik.demis.service.base.error.ServiceException;
import de.gematik.demis.service.base.error.rest.ErrorFieldProvider;
import de.gematik.demis.service.base.error.rest.SampleRestController;
import de.gematik.demis.service.base.error.rest.SampleService;
import java.time.LocalDateTime;
import org.assertj.core.api.Assertions;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(
    classes = FhirRestExceptionHandlerIntegrationTest.TestApp.class,
    properties = "base.fhir.operation-outcome.profile=" + PROFILE)
@ActiveProfiles("fhir")
@AutoConfigureMockMvc
class FhirRestExceptionHandlerIntegrationTest {

  public static final String PROFILE = "http=//demis.de/test";
  private static final String ERROR_ID = "123-abc";
  private static final String TIMESTAMP = "2023-11-06T19:19:21.525781612";
  private static final String URL_GET = "/sample/2";
  @MockitoBean SampleService service;
  @MockitoBean ErrorFieldProvider errorFieldProvider;

  @Autowired MockMvc mockMvc;
  @Autowired FhirContext fhirContext;

  @BeforeEach
  void setup() {
    when(errorFieldProvider.generateId()).thenReturn(ERROR_ID);
    when(errorFieldProvider.timestamp()).thenReturn(LocalDateTime.parse(TIMESTAMP));
  }

  @ParameterizedTest
  @EnumSource(MessageType.class)
  void errorAsOperationOutcome(final MessageType messageType) throws Exception {
    final HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
    final String errorCode = "TEST-ERROR";
    final String message = "my detail";

    Mockito.doThrow(new ServiceException(status, errorCode, message)).when(service).doSomething();

    final OperationOutcome expectedOperationOutcome = new OperationOutcome();
    expectedOperationOutcome.getMeta().addProfile(PROFILE);
    expectedOperationOutcome
        .addIssue()
        .setSeverity(OperationOutcome.IssueSeverity.ERROR)
        .setCode(OperationOutcome.IssueType.EXCEPTION)
        .setDetails(new CodeableConcept().addCoding(new Coding().setCode(errorCode)))
        .setDiagnostics(message)
        .addLocation(ERROR_ID);

    executeTest(messageType, status, expectedOperationOutcome);
  }

  @Test
  void errorWithOperationOutcome() throws Exception {
    final HttpStatus status = HttpStatus.UNPROCESSABLE_ENTITY;
    final String errorCode = "TEST-VALIDATION-ERROR";
    final String message = "my detail";

    final OperationOutcome operationOutcome = new OperationOutcome();
    operationOutcome
        .addIssue()
        .setSeverity(OperationOutcome.IssueSeverity.ERROR)
        .setCode(OperationOutcome.IssueType.PROCESSING)
        .setDiagnostics("validation error");

    Mockito.doThrow(
            new ServiceOperationOutcomeException(status, errorCode, message, operationOutcome))
        .when(service)
        .doSomething();

    final OperationOutcome expectedOperationOutcome = new OperationOutcome();
    expectedOperationOutcome.getMeta().addProfile(PROFILE);
    expectedOperationOutcome.addIssue(operationOutcome.getIssue().getFirst());
    expectedOperationOutcome
        .addIssue()
        .setSeverity(OperationOutcome.IssueSeverity.ERROR)
        .setCode(OperationOutcome.IssueType.PROCESSING)
        .setDetails(new CodeableConcept().addCoding(new Coding().setCode(errorCode)))
        .setDiagnostics(message)
        .addLocation(ERROR_ID);

    executeTest(MessageType.JSON, status, expectedOperationOutcome);
  }

  private void executeTest(
      final MessageType messageType,
      final HttpStatus expectedStatus,
      final OperationOutcome expectedOperationOutcome)
      throws Exception {
    final MediaType acceptHeader =
        messageType == MessageType.XML ? MediaType.APPLICATION_XML : MediaType.APPLICATION_JSON;

    final String body =
        performValidRequest(acceptHeader)
            .andExpect(status().is(expectedStatus.value()))
            .andReturn()
            .getResponse()
            .getContentAsString();

    final IParser fhirParser =
        messageType == MessageType.XML ? fhirContext.newXmlParser() : fhirContext.newJsonParser();
    final OperationOutcome actualOperationOutcome =
        fhirParser.parseResource(OperationOutcome.class, body);
    Assertions.assertThat(actualOperationOutcome)
        .usingRecursiveComparison()
        .ignoringFields("userData", "text")
        .isEqualTo(expectedOperationOutcome);
  }

  private ResultActions performValidRequest(final MediaType acceptHeader) throws Exception {
    return mockMvc.perform(get(URL_GET).queryParam("myQueryParam", "5").accept(acceptHeader));
  }

  @SpringBootApplication
  @Import(SampleRestController.class)
  static class TestApp {}
}
