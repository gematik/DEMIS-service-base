package de.gematik.demis.service.base.fhir.outcome;

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
import static org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity.ERROR;
import static org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity.FATAL;
import static org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity.INFORMATION;
import static org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity.WARNING;
import static org.mockito.Mockito.when;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r4.model.OperationOutcome.OperationOutcomeIssueComponent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FhirOperationOutcomeServiceTest {

  @Mock FhirOperationOutcomeProperties props;
  @InjectMocks FhirOperationOutcomeService underTest;

  private static IParser jsonFhirParser() {
    return FhirContext.forR4Cached().newJsonParser().setPrettyPrint(true);
  }

  @Test
  void processOutcome_applyProfile() {
    final String profile = "http://demis.de/test-profile";
    when(props.profile()).thenReturn(profile);
    final Meta expectedMeta = new Meta().addProfile(profile);

    final OperationOutcome operationOutcome = new OperationOutcome();
    underTest.processOutcome(operationOutcome);
    assertThat(operationOutcome.getMeta()).usingRecursiveComparison().isEqualTo(expectedMeta);

    // is idempotent (second call do nothing)
    underTest.processOutcome(operationOutcome);
    assertThat(operationOutcome.getMeta()).usingRecursiveComparison().isEqualTo(expectedMeta);
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = ';',
      value = {
        "INFORMATION; INFORMATION, WARNING, ERROR, FATAL",
        "WARNING; WARNING, ERROR, FATAL",
        "ERROR; ERROR, FATAL",
        "FATAL; FATAL",
      })
  void processOutcome_filter(final IssueSeverity threshold, final String allowedSeveritiesString) {
    final Set<IssueSeverity> allowedSeverities =
        Arrays.stream(allowedSeveritiesString.split("\\s*,\\s*"))
            .map(IssueSeverity::valueOf)
            .collect(Collectors.toSet());

    final IssueSeverity[] inputSeverities =
        new IssueSeverity[] {ERROR, WARNING, INFORMATION, FATAL, INFORMATION, WARNING, ERROR};
    final List<IssueSeverity> expectedSeverities =
        Arrays.stream(inputSeverities).filter(allowedSeverities::contains).toList();

    when(props.issueFilter()).thenReturn(true);
    when(props.issueThreshold()).thenReturn(threshold);
    when(props.sort()).thenReturn(false);

    final OperationOutcome operationOutcome = createOperationOutcome(inputSeverities);
    underTest.processOutcome(operationOutcome);

    assertThat(operationOutcome.getIssue())
        .map(OperationOutcomeIssueComponent::getSeverity)
        .containsExactlyElementsOf(expectedSeverities);
  }

  @Test
  void processOutcome_sort() {
    when(props.sort()).thenReturn(true);
    when(props.issueFilter()).thenReturn(false);

    final OperationOutcome operationOutcome =
        createOperationOutcome(ERROR, WARNING, INFORMATION, FATAL, INFORMATION, WARNING, ERROR);
    underTest.processOutcome(operationOutcome);
    assertThat(operationOutcome.getIssue())
        .map(OperationOutcomeIssueComponent::getSeverity)
        .containsExactly(FATAL, ERROR, ERROR, WARNING, WARNING, INFORMATION, INFORMATION);
  }

  @Test
  void processOutcome_text() {
    final String expected =
"""
{
  "resourceType": "OperationOutcome",
  "text": {
    "status": "generated",
    "div": "<div xmlns=\\"http://www.w3.org/1999/xhtml\\"></div>"
  }
}
""";
    final IParser jsonParser = jsonFhirParser();
    final var operationOutcome = new OperationOutcome();
    underTest.processOutcome(operationOutcome);
    assertThat(jsonParser.encodeResourceToString(operationOutcome))
        .isEqualToIgnoringWhitespace(expected);
  }

  @Test
  void allOkay() {
    final String expected =
"""
{
  "severity" : "information",
  "code" : "informational",
  "details" : {
    "text" : "All OK"
  }
}
""";
    final OperationOutcomeIssueComponent expectedIssue = new OperationOutcomeIssueComponent();
    jsonFhirParser().parseInto(expected, expectedIssue);
    final OperationOutcomeIssueComponent actualIssue = underTest.allOk();
    assertThat(actualIssue).usingRecursiveComparison().isEqualTo(expectedIssue);
  }

  private OperationOutcome createOperationOutcome(final IssueSeverity... issueSeverities) {
    final OperationOutcome operationOutcome = new OperationOutcome();
    Arrays.stream(issueSeverities)
        .forEach(issueSeverity -> operationOutcome.addIssue(createIssue(issueSeverity)));
    return operationOutcome;
  }

  private OperationOutcomeIssueComponent createIssue(final IssueSeverity severity) {
    final var issue = new OperationOutcomeIssueComponent();
    issue
        .setCode(OperationOutcome.IssueType.PROCESSING)
        .setSeverity(severity)
        .getDetails()
        .setText("Just for testing");
    return issue;
  }
}
