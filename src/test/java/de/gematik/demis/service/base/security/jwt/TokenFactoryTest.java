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

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class TokenFactoryTest {

  private static final String VALID_AUTHENTICATOR_AUTH_HEADER =
      "Bearer eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJyUldwMUlGX0hNQ0EwZFFpVHA2X2VMVDNKSXdmNVVnalA3M05HZnFEYllNIn0.eyJleHAiOjE3MTQ2NTk1MjUsImlhdCI6MTcxNDY1OTQ2NSwiYXV0aF90aW1lIjoxNzE0NjU5NDY0LCJqdGkiOiI3M2VmOTc5MS03MTFkLTQ5OWYtOTFmMi1mYmRlMWFiNDIwYWYiLCJpc3MiOiJodHRwczovL2F1dGgucXMuZGVtaXMucmtpLmRlL3JlYWxtcy9QT1JUQUwiLCJhdWQiOiJhY2NvdW50Iiwic3ViIjoiMGIwMmQ3YTYtMmYxMy00ZTcwLTljNDItMWVhN2IwNTM0MjkyIiwidHlwIjoiQmVhcmVyIiwiYXpwIjoibWVsZGVwb3J0YWwiLCJub25jZSI6Ijk0YTg1MGFkMjhmZmEyMDY4ZDJhNTU0MjUwN2FhMWZmNWVTdFVJSFdaIiwic2Vzc2lvbl9zdGF0ZSI6ImQzZjFjNjM4LTJiNzYtNGYyNi04OTIyLTllODAwNTAwNzMyOCIsImFjciI6IjEiLCJhbGxvd2VkLW9yaWdpbnMiOlsiaHR0cHM6Ly9tZWxkdW5nLnFzLmRlbWlzLnJraS5kZSIsImh0dHBzOi8vcG9ydGFsLnFzLmRlbWlzLnJraS5kZSJdLCJyZWFsbV9hY2Nlc3MiOnsicm9sZXMiOlsiYmVkLW9jY3VwYW5jeS1zZW5kZXIiLCJkaXNlYXNlLW5vdGlmaWNhdGlvbi1zZW5kZXIiLCJvZmZsaW5lX2FjY2VzcyIsImRlZmF1bHQtcm9sZXMtcG9ydGFsIiwidW1hX2F1dGhvcml6YXRpb24iLCJwYXRob2dlbi1ub3RpZmljYXRpb24tc2VuZGVyIiwidmFjY2luZS1pbmp1cnktc2VuZGVyIl19LCJyZXNvdXJjZV9hY2Nlc3MiOnsiYWNjb3VudCI6eyJyb2xlcyI6WyJtYW5hZ2UtYWNjb3VudCIsIm1hbmFnZS1hY2NvdW50LWxpbmtzIiwidmlldy1wcm9maWxlIl19fSwic2NvcGUiOiJvcGVuaWQgZW1haWwgcHJvZmlsZSIsInNpZCI6ImQzZjFjNjM4LTJiNzYtNGYyNi04OTIyLTllODAwNTAwNzMyOCIsImlrIjoiMTIzNDk0NTQ2IiwiZW1haWxfdmVyaWZpZWQiOmZhbHNlLCJwcm9mZXNzaW9uT2lkIjoiMS4yLjI3Ni4wLjc2LjQuNTMiLCJvcmdhbml6YXRpb25OYW1lIjoiS3JhbmtlbmhhdXMgTWVsaXNzYSBEYXZpZCBURVNULU9OTFkiLCJhY2NvdW50VHlwZSI6Im9yZ2FuaXphdGlvbiIsImFjY291bnRTb3VyY2UiOiJnZW1hdGlrIiwiYWNjb3VudElzVGVtcG9yYXJ5Ijp0cnVlLCJhY2NvdW50SWRlbnRpZmllciI6Imh0dHBzOi8vZ2VtYXRpay5kZS9maGlyL3NpZC90ZWxlbWF0aWstaWR8NS0yLTEyMzQ5NDU0NiIsInByZWZlcnJlZF91c2VybmFtZSI6IjUtMi0xMjM0OTQ1NDYiLCJsZXZlbE9mQXNzdXJhbmNlIjoiU1RPUkstUUFBLUxldmVsLTMiLCJ1c2VybmFtZSI6IjUtMi0xMjM0OTQ1NDYifQ.JCAKFAlQMXrMTSUJbEtZMi-Kbj-3DWbcJnWgKNQH5B3r3ovLekrDWsROh_68TKuGMmrsSHhl_8MMesyQxSudAsMNGNbt6KLmRKDMX5zDn_MiUvvIyr7W1H-t3SDJEQ6WVAlFTdLFvo0GOVkeh22oXVpbJHcQ8UosEnJVRir4W2C5JftVI6bljJpgd5dvUxTF8qoUAUfvxzbzukt8MN63cgTEsgSu_QTobnKgdusgVahvXF99SqLWHfJTszvk8Ov92-4nlfnOdGVvCoVl577c2HHbZjLl5Qnj1HPQ2P2r-zpy70rcm2N3XnHmliJWQUeiT1NF91LBkkjvl3xBOqB7FA";

  @Test
  void shouldReturnTokenGivenValidHeaders() {
    final Token expected = expectedAuthenticatorToken();
    final HttpHeaders headers = configureHeaders();
    final TokenFactory tokenFactory = new TokenFactory(headers);

    final Token token = tokenFactory.get();

    assertThat(token).usingRecursiveComparison().isEqualTo(expected);
  }

  @Test
  void shouldReturnTokenGivenValidAuthHeader() {
    final Token expected = expectedAuthenticatorToken();
    final TokenFactory tokenFactory = new TokenFactory(VALID_AUTHENTICATOR_AUTH_HEADER);

    final Token token = tokenFactory.get();

    assertThat(token).usingRecursiveComparison().isEqualTo(expected);
  }

  private HttpHeaders configureHeaders() {
    final HttpHeaders headers = new HttpHeaders();
    headers.add(HttpHeaders.AUTHORIZATION, VALID_AUTHENTICATOR_AUTH_HEADER);
    return headers;
  }

  private Token expectedAuthenticatorToken() {
    return DemisToken.builder()
        .accountIsTest(false)
        .azp("meldeportal")
        .ik("123494546")
        .preferredUsername("5-2-123494546")
        .roles(
            List.of(
                "bed-occupancy-sender",
                "disease-notification-sender",
                "offline_access",
                "default-roles-portal",
                "uma_authorization",
                "pathogen-notification-sender",
                "vaccine-injury-sender"))
        .build();
  }
}
