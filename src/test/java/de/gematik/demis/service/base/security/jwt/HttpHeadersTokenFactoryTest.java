package de.gematik.demis.service.base.security.jwt;

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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class HttpHeadersTokenFactoryTest {

  private static final String VALID_AUTHORIZATION_HEADER =
      "Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsImtpZCI6ImU2MmI2ZjI3MDc1ZjA2ZTA2NmI4ZTc0YTEyM2NjM2RmIn0.eyJleHAiOjE3MzIwOTM1MzcsImlhdCI6MTczMjA5MjkzNywianRpIjoiNTFmZTlhZTItNjUwNS00OGM2LWJiMmMtNGUzZWQ4OWFiNWFlIiwiaXNzIjoiaHR0cHM6Ly9hdXRoLmluZ3Jlc3MubG9jYWwvcmVhbG1zL09FR0QiLCJhdWQiOlsibm90aWZpY2F0aW9uLWNsZWFyaW5nLWFwaSIsImFjY291bnQiXSwic3ViIjoiZjlmMWE1ZTctOGFiNS00MzAwLThiMjMtYzhiM2ZmZjBjMzdiIiwidHlwIjoiQmVhcmVyIiwiYXpwIjoiZGVtaXMtaW1wb3J0ZXIiLCJzaWQiOiI4YWJhMmZjOC1lMTUzLTRiM2MtODU2NS04MjA5Mjk0ODg0OTkiLCJyZWFsbV9hY2Nlc3MiOnsicm9sZXMiOlsicGF0aG9nZW4tbm90aWZpY2F0aW9uLWZldGNoZXIiLCJ2YWNjaW5lLWluanVyeS1mZXRjaGVyIiwiZGlzZWFzZS1ub3RpZmljYXRpb24tZmV0Y2hlciJdfSwicmVzb3VyY2VfYWNjZXNzIjp7Im5vdGlmaWNhdGlvbi1jbGVhcmluZy1hcGkiOnsicm9sZXMiOlsibGFiLW5vdGlmaWNhdGlvbi1yZWNlaXZlciJdfSwiYWNjb3VudCI6eyJyb2xlcyI6WyJtYW5hZ2UtYWNjb3VudCIsIm1hbmFnZS1hY2NvdW50LWxpbmtzIiwidmlldy1wcm9maWxlIl19fSwic2NvcGUiOiJwcm9maWxlIiwib3JnYW5pemF0aW9uIjoiS3JlaXMgSGVyem9ndHVtIEdyb8OfLUzDvG5lYnVyZyIsInByZWZlcnJlZF91c2VybmFtZSI6IjEuMDEuMC41My4ifQ.X8rBojhqKh8Oxz-3WscEYvtfT1R0hRKHv1cN0xD6RyCortDMCaGW5FaX9kOYIRS9td7jkCqOpBiR2tL7DyyovYqZXHMBGUnJjPpfg1krezcSqDmpNc4eox4McjixCnTdnxiUpdYNLKuWOWsRV6-tz8cNXTbTT4eVeLAQ49fqfkLNDoKsWwEDSywj7WznL0p6C-azUlWHARO_SNtEQ0wQS9TdIjIYP7MMycE3yyI0ZJRwBotwwvO-xsiOLECKYZ8zT9qSkF5I12DvhqdlMhrprIimA21D1qZPo4BTZwhsDTzNzNJvPSlYS_pfiN6QCNKtK2-ps6BeA_HO6bcobqr36JsXZmOF2qMBEVsYxcEb76CI0Af8HQvCWwve_Sr-QKv-f3AfUV7FDGQSG_uluZCdmOwPRxuxmiwZtOA7bWIkOhELXu5bPboZ2otH3I0mug28xe-YVAp5SYBArMxijtgbHUCOcLLJ0l4_cRbTo8Yw99-Zr_RDhtxG4JyFBxtBRA2h";

  @Test
  void shouldReturnTokenWhenHeadersAreValid() {
    final HttpHeaders validHeaders = configureValidHeaders();
    final Token expected =
        DemisToken.builder()
            .azp("demis-importer")
            .accountIsTest(false)
            .ik(null)
            .preferredUsername("1.01.0.53.")
            .roles(
                List.of(
                    "pathogen-notification-fetcher",
                    "vaccine-injury-fetcher",
                    "disease-notification-fetcher"))
            .build();
    final HttpHeadersTokenFactory underTest = new HttpHeadersTokenFactory(validHeaders);

    final Token token = underTest.get();

    assertThat(token).usingRecursiveComparison().isEqualTo(expected);
  }

  private HttpHeaders configureValidHeaders() {
    final HttpHeaders headers = new HttpHeaders();
    headers.add(HttpHeaders.AUTHORIZATION, VALID_AUTHORIZATION_HEADER);
    return headers;
  }

  @Test
  void shouldThrowExceptionWhenHeadersAreInvalid() {
    final HttpHeaders invalidHeaders = new HttpHeaders();
    final HttpHeadersTokenFactory underTest = new HttpHeadersTokenFactory(invalidHeaders);

    assertThrows(
        IllegalArgumentException.class,
        underTest::get,
        "Exactly one Authorization header expected");
  }

  @Test
  void shouldThrowExceptionWhenAuthorizationHeaderIsEmpty() {
    final HttpHeaders headers = new HttpHeaders();
    headers.add(HttpHeaders.AUTHORIZATION, "");
    final HttpHeadersTokenFactory underTest = new HttpHeadersTokenFactory(headers);

    assertThrows(
        IllegalArgumentException.class, underTest::get, "Authorization header must not be empty");
  }

  @Test
  void shouldThrowExceptionWhenMultipleAuthorizationHeaders() {
    final HttpHeaders headers = new HttpHeaders();
    headers.add(HttpHeaders.AUTHORIZATION, VALID_AUTHORIZATION_HEADER);
    headers.add(HttpHeaders.AUTHORIZATION, VALID_AUTHORIZATION_HEADER);
    final HttpHeadersTokenFactory underTest = new HttpHeadersTokenFactory(headers);

    assertThrows(
        IllegalArgumentException.class,
        underTest::get,
        "Exactly one Authorization header expected");
  }

  @Test
  void shouldThrowExceptionWhenAuthorizationHeaderIsMalformed() {
    final HttpHeaders headers = new HttpHeaders();
    headers.add(HttpHeaders.AUTHORIZATION, "InvalidToken");
    final HttpHeadersTokenFactory underTest = new HttpHeadersTokenFactory(headers);

    assertThrows(
        IllegalArgumentException.class,
        underTest::get,
        "Authorization header must contain a valid Bearer token");
  }
}
