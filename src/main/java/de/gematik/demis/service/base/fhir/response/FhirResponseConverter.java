package de.gematik.demis.service.base.fhir.response;

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
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 * #L%
 */

import ca.uhn.fhir.context.FhirContext;
import de.gematik.demis.fhirparserlibrary.FhirParser;
import de.gematik.demis.fhirparserlibrary.MessageType;
import jakarta.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ResponseEntity.BodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.WebRequest;

@Service
@RequiredArgsConstructor
public class FhirResponseConverter {

  private static final String PARAM_FORMAT = "_format";
  private static final String PARAM_FORMAT_VALUE_JSON = "json";
  private static final String PARAM_FORMAT_VALUE_XML = "xml";

  private static final MediaType CONTENT_TYPE_JSON =
      new MediaType("application", "fhir+json", StandardCharsets.UTF_8);
  private static final MediaType CONTENT_TYPE_XML =
      new MediaType("application", "fhir+xml", StandardCharsets.UTF_8);

  private final FhirContext fhirContext;
  private final FhirResponseConverterProperties props;

  /**
   * accept header has priority. only if accept header is wildcard or is not set, content-type
   * header is used
   */
  @Nullable
  private static String getMediaTypeFromHeader(final WebRequest webRequest) {
    final String accept = webRequest.getHeader(HttpHeaders.ACCEPT);
    if (accept != null && !accept.contains("*")) {
      return accept;
    }
    // fallback
    return webRequest.getHeader(HttpHeaders.CONTENT_TYPE);
  }

  @Nullable
  private static MessageType getMessageTypeFromHeader(final WebRequest webRequest) {
    final String mediaTypeFromHeader = getMediaTypeFromHeader(webRequest);
    try {
      return mediaTypeFromHeader != null ? MessageType.getMessageType(mediaTypeFromHeader) : null;
    } catch (final IllegalArgumentException ex) {
      // ignore
      return null;
    }
  }

  @Nullable
  private static MessageType getMessageTypeFromRequestParameter(final WebRequest webRequest) {
    final String value = webRequest.getParameter(PARAM_FORMAT);
    if (value != null) {
      return switch (value.toLowerCase()) {
        case PARAM_FORMAT_VALUE_JSON -> MessageType.JSON;
        case PARAM_FORMAT_VALUE_XML -> MessageType.XML;
        default -> null;
      };
    }
    return null;
  }

  private static MediaType getContentType(final MessageType messageType) {
    return switch (messageType) {
      case JSON -> CONTENT_TYPE_JSON;
      case XML -> CONTENT_TYPE_XML;
    };
  }

  private MediaType determineOutputMediaTypeLegacy(final WebRequest webRequest) {
    return Optional.ofNullable(getMediaTypeFromHeader(webRequest))
        .map(MediaType::parseMediaType)
        .orElse(MediaType.APPLICATION_JSON);
  }

  /**
   * <strong>Strategy:</strong>
   *
   * <ol>
   *   <li>Request parameter <code>_format</code>
   *   <li>Accept header
   *   <li>Content-type header
   *   <li>Default: JSON
   * </ol>
   *
   * Note: Invalid values are ignored.
   */
  private MessageType determineOutputFormat(final WebRequest webRequest) {
    MessageType result = getMessageTypeFromRequestParameter(webRequest);
    if (result == null) {
      result = getMessageTypeFromHeader(webRequest);
    }
    return result != null ? result : MessageType.JSON;
  }

  // TODO buildResponse
  public ResponseEntity<Object> buildResponse(
      final BodyBuilder bodyBuilder, final IBaseResource resource, final WebRequest webRequest) {
    final MediaType outputContentType;
    final MessageType outputMessageType;
    if (props.contentTypeValueLegacyLogic()) {
      outputContentType =
          new MediaType(determineOutputMediaTypeLegacy(webRequest), StandardCharsets.UTF_8);
      outputMessageType = MessageType.getMessageType(outputContentType.getSubtype());
    } else {
      outputMessageType = determineOutputFormat(webRequest);
      outputContentType = getContentType(outputMessageType);
    }

    return bodyBuilder
        .contentType(outputContentType)
        .body(new FhirParser(fhirContext).encode(resource, outputMessageType));
  }
}
