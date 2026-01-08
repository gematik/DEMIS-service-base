package de.gematik.demis.service.base.fhir.response;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import ca.uhn.fhir.context.FhirContext;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

class FhirResponseConverterTest {
  private static final String EXPECTED_JSON =
"""
{"resourceType":"OperationOutcome","id":"TEST-ID"}
""";
  private static final String EXPECTED_XML =
"""
<OperationOutcome xmlns="http://hl7.org/fhir"><id value="TEST-ID"></id></OperationOutcome>
""";
  FhirResponseConverterProperties props;
  FhirResponseConverter underTest;

  @BeforeEach
  void setUp() {
    props = Mockito.mock(FhirResponseConverterProperties.class);
    underTest = new FhirResponseConverter(FhirContext.forR4Cached(), props);
  }

  @CsvSource({
    // permute accept
    "application/json, application/json, application/fhir+json",
    "application/json, application/json+fhir, application/fhir+json",
    "application/json, application/fhir+json, application/fhir+json",
    "application/json, application/xml, application/fhir+xml",
    "application/json, application/xml+fhir, application/fhir+xml",
    "application/json, application/fhir+xml, application/fhir+xml",
    "application/json, text/xml, application/fhir+xml",
    // wildcard accept, permute content type
    "application/json, */*, application/fhir+json",
    "application/json;charset=UTF-8, */*, application/fhir+json",
    "application/json+fhir, */*, application/fhir+json",
    "application/fhir+json, */*, application/fhir+json",
    "application/xml, */*, application/fhir+xml",
    "application/xml;charset=UTF-8, */*, application/fhir+xml",
    "application/xml+fhir, */*, application/fhir+xml",
    "application/fhir+xml, */*, application/fhir+xml",
    "text/xml,*/*, application/fhir+xml",
    "text/xml;charset=UTF-8, */*, application/fhir+xml",
    // accept not set (same as wildcard)
    "application/xml, , application/fhir+xml",
    // nothing set -> default
    ", , application/fhir+json",
  })
  @ParameterizedTest
  void defaultConverting(
      final String contentType, final String accept, final String expectedContentType) {
    executeTest(contentType, accept, expectedContentType);
  }

  @CsvSource({
    "application/xml, application/xml, json, application/fhir+json",
    "application/json, application/json, xml, application/fhir+xml",
    "application/xml, application/xml, unknown, application/fhir+xml",
    "application/xml, application/json, unknown, application/fhir+json",
  })
  @ParameterizedTest
  void formatParameter(
      final String contentType,
      final String accept,
      final String formatValue,
      final String expectedContentType) {
    executeTest(contentType, accept, formatValue, expectedContentType);
  }

  @CsvSource({
    // permute accept
    "application/json, application/json, application/json",
    "application/json, application/json+fhir, application/json+fhir",
    "application/json, application/fhir+json, application/fhir+json",
    "application/json, application/xml, application/xml",
    "application/json, application/xml+fhir, application/xml+fhir",
    "application/json, application/fhir+xml, application/fhir+xml",
    "application/json, text/xml, text/xml",
    // wildcard accept, permute content type
    "application/json, */*, application/json",
    "application/json;charset=UTF-8, */*, application/json",
    "application/json+fhir, */*, application/json+fhir",
    "application/fhir+json, */*, application/fhir+json",
    "application/xml, */*, application/xml",
    "application/xml;charset=UTF-8, */*, application/xml",
    "application/xml+fhir, */*, application/xml+fhir",
    "application/fhir+xml, */*, application/fhir+xml",
    "text/xml,*/*, text/xml",
    "text/xml;charset=UTF-8, */*, text/xml",
    // accept not set (same as wildcard)
    "application/xml, , application/xml",
    // nothing set -> default
    ", , application/json",
  })
  @ParameterizedTest
  void legacyConverting(
      final String contentType, final String accept, final String expectedContentType) {
    when(props.contentTypeValueLegacyLogic()).thenReturn(true);
    executeTest(contentType, accept, expectedContentType);
  }

  private void executeTest(
      final String contentType, final String accept, final String expectedContentType) {
    executeTest(contentType, accept, null, expectedContentType);
  }

  private void executeTest(
      final String contentType,
      final String accept,
      final String formatParameter,
      final String expectedContentType) {
    final MockHttpServletRequest servletRequest = new MockHttpServletRequest();
    if (contentType != null) {
      servletRequest.addHeader("Content-Type", contentType);
    }
    if (accept != null) {
      servletRequest.addHeader("ACCEPT", accept);
    }
    if (formatParameter != null) {
      servletRequest.addParameter("_format", formatParameter);
    }
    final WebRequest webRequest = new ServletWebRequest(servletRequest);

    final OperationOutcome fhirResource = new OperationOutcome();
    fhirResource.setId("TEST-ID");

    final ResponseEntity<Object> result =
        underTest.buildResponse(ResponseEntity.ok(), fhirResource, webRequest);

    final String expectedContentTypeWithCharset = expectedContentType + ";charset=UTF-8";
    final String expectedContent =
        expectedContentType.contains("json") ? EXPECTED_JSON : EXPECTED_XML;
    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(result.getHeaders().getContentType()).hasToString(expectedContentTypeWithCharset);
    assertThat(result.getBody()).asString().isEqualToNormalizingWhitespace(expectedContent);
  }
}
