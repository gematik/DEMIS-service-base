package de.gematik.demis.service.base.fhir.cleanup;

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

import static de.gematik.demis.service.base.fhir.cleanup.ExtensionTestFixtures.UNEXPECTED;
import static de.gematik.demis.service.base.fhir.cleanup.ExtensionTestFixtures.addIssue;
import static de.gematik.demis.service.base.fhir.cleanup.ExtensionTestFixtures.addUnexpectedExtensionIssue;
import static de.gematik.demis.service.base.fhir.cleanup.ExtensionTestFixtures.bundleWithPatient;
import static de.gematik.demis.service.base.fhir.cleanup.ExtensionTestFixtures.extension;
import static de.gematik.demis.service.base.fhir.cleanup.ExtensionTestFixtures.patient;
import static de.gematik.demis.service.base.fhir.cleanup.ExtensionTestFixtures.unexpectedExtensionOutcome;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.gematik.demis.service.base.fhir.cleanup.OrphanedResourceRemover.RemovedEntry;
import java.util.List;
import java.util.Set;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.OperationOutcomeIssueComponent;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExtensionCleanupServiceTest {

  private static final String LOCATION_0 = "Bundle.entry[1].resource.extension[0]";
  private static final String LOCATION_1 = "Bundle.entry[1].resource.extension[1]";

  private final Bundle bundle = bundleWithPatient(patient());

  @Mock ExtensionRemover extensionRemover;
  @Mock OrphanedResourceRemover orphanedResourceRemover;
  @InjectMocks ExtensionCleanupService underTest;

  @Nested
  class LocationSelection {

    @Test
    void shouldPassLocationsOfUnexpectedExtensionIssues() {
      underTest.cleanupUnexpectedExtensions(
          bundle, unexpectedExtensionOutcome(LOCATION_0, LOCATION_1));

      verify(extensionRemover).remove(bundle, Set.of(LOCATION_0, LOCATION_1));
    }

    @Test
    void shouldIgnoreLocationsOfOtherIssues() {
      final OperationOutcome outcome = new OperationOutcome();
      addIssue(outcome, "Some_Other_MessageId", LOCATION_0);
      addUnexpectedExtensionIssue(outcome, LOCATION_1);

      underTest.cleanupUnexpectedExtensions(bundle, outcome);

      verify(extensionRemover).remove(bundle, Set.of(LOCATION_1));
    }

    @Test
    void shouldIgnoreIssuesWithoutLocation() {
      final OperationOutcome outcome = new OperationOutcome();
      addUnexpectedExtensionIssue(outcome, null);

      underTest.cleanupUnexpectedExtensions(bundle, outcome);

      verifyNoInteractions(extensionRemover);
    }

    @Test
    void shouldPassDuplicatedLocationOnlyOnce() {
      underTest.cleanupUnexpectedExtensions(
          bundle, unexpectedExtensionOutcome(LOCATION_0, LOCATION_0));

      verify(extensionRemover).remove(bundle, Set.of(LOCATION_0));
    }
  }

  @Nested
  class NothingToDo {

    @Test
    void shouldDoNothingWithoutUnexpectedExtensionIssues() {
      final OperationOutcome outcome = new OperationOutcome();
      addIssue(outcome, "Some_Other_MessageId", LOCATION_0);

      underTest.cleanupUnexpectedExtensions(bundle, outcome);

      verifyNoInteractions(extensionRemover);
    }

    @Test
    void shouldDoNothingForEmptyOutcome() {
      underTest.cleanupUnexpectedExtensions(bundle, new OperationOutcome());

      verifyNoInteractions(extensionRemover);
    }

    @Test
    void shouldDoNothingForNullOutcome() {
      underTest.cleanupUnexpectedExtensions(bundle, null);

      verifyNoInteractions(extensionRemover);
    }

    @Test
    void shouldDoNothingForNullBundle() {
      underTest.cleanupUnexpectedExtensions(null, unexpectedExtensionOutcome(LOCATION_0));

      verifyNoInteractions(extensionRemover);
    }
  }

  @Nested
  class OrphanedResourceHandling {

    @Test
    void shouldNotRemoveOrphanedResourcesWithoutUnexpectedExtensionIssues() {
      underTest.cleanupUnexpectedExtensions(bundle, new OperationOutcome());
      underTest.cleanupUnexpectedExtensions(null, unexpectedExtensionOutcome(LOCATION_0));
      underTest.cleanupUnexpectedExtensions(bundle, null);

      verifyNoInteractions(orphanedResourceRemover);
    }

    @Test
    void shouldPassRemovedExtensionsToOrphanedResourceRemover() {
      final List<Extension> removed = List.of(extension(UNEXPECTED));
      when(extensionRemover.remove(bundle, Set.of(LOCATION_0))).thenReturn(removed);

      underTest.cleanupUnexpectedExtensions(bundle, unexpectedExtensionOutcome(LOCATION_0));

      verify(orphanedResourceRemover).removeReferencedResourcesIfOrphaned(bundle, removed);
    }
  }

  @Nested
  class WarningCreation {

    private static final String EXTENSION_WARNING_TEXT =
        "%d unerwartete Extension(s) wurden entfernt";
    private static final String RESOURCE_WARNING_TEXT =
        "Resource '%s' wurde aus dem Bundle entfernt, da sie nicht mehr referenziert wird.";

    /** Runs the cleanup and returns the issues that were not part of the outcome before. */
    private List<OperationOutcomeIssueComponent> executeTestAndGetAddedIssues(
        final OperationOutcome outcome) {
      final List<OperationOutcomeIssueComponent> issuesBefore = outcome.copy().getIssue();

      underTest.cleanupUnexpectedExtensions(bundle, outcome);

      return outcome.getIssue().stream()
          .filter(issue -> issuesBefore.stream().noneMatch(issue::equalsDeep))
          .toList();
    }

    private static void assertExtensionWarning(
        final OperationOutcomeIssueComponent issue, final int removedExtensions) {
      assertWarning(issue, EXTENSION_WARNING_TEXT.formatted(removedExtensions));
    }

    private static void assertResourceWarning(
        final OperationOutcomeIssueComponent issue,
        final String resourceName,
        final String location) {
      assertWarning(issue, RESOURCE_WARNING_TEXT.formatted(resourceName), location);
    }

    private static void assertWarning(
        final OperationOutcomeIssueComponent issue,
        final String expectedDiagnostics,
        final String... expectedLocations) {
      assertThat(issue.getSeverity()).isEqualTo(OperationOutcome.IssueSeverity.WARNING);
      assertThat(issue.getCode()).isEqualTo(OperationOutcome.IssueType.INFORMATIONAL);
      assertThat(issue.getDiagnostics()).isEqualTo(expectedDiagnostics);
      assertThat(issue.getLocation())
          .extracting(StringType::getValue)
          .containsExactly(expectedLocations);
    }

    @Test
    void shouldAddWarningWithFullUrlAndLocationForEachRemovedEntry() {
      final List<Extension> removed = List.of(extension(UNEXPECTED));
      when(extensionRemover.remove(bundle, Set.of(LOCATION_0))).thenReturn(removed);
      when(orphanedResourceRemover.removeReferencedResourcesIfOrphaned(bundle, removed))
          .thenReturn(
              List.of(new RemovedEntry("urn:uuid:1234", 2), new RemovedEntry("urn:uuid:5678", 4)));

      final var newIssues = executeTestAndGetAddedIssues(unexpectedExtensionOutcome(LOCATION_0));

      assertThat(newIssues)
          .as("one warning for the removed extension, one for each removed resource")
          .hasSize(3)
          .satisfiesExactlyInAnyOrder(
              issue -> assertExtensionWarning(issue, 1),
              issue -> assertResourceWarning(issue, "urn:uuid:1234", "Bundle.entry[2]"),
              issue -> assertResourceWarning(issue, "urn:uuid:5678", "Bundle.entry[4]"));
    }

    @Test
    void shouldUseLocationInTextIfFullUrlIsMissing() {
      final List<Extension> removed = List.of(extension(UNEXPECTED));
      when(extensionRemover.remove(bundle, Set.of(LOCATION_0))).thenReturn(removed);
      when(orphanedResourceRemover.removeReferencedResourcesIfOrphaned(bundle, removed))
          .thenReturn(List.of(new RemovedEntry(null, 3)));

      final var newIssues = executeTestAndGetAddedIssues(unexpectedExtensionOutcome(LOCATION_0));

      assertThat(newIssues)
          .hasSize(2)
          .satisfiesExactlyInAnyOrder(
              issue -> assertExtensionWarning(issue, 1),
              issue -> assertResourceWarning(issue, "Bundle.entry[3]", "Bundle.entry[3]"));
    }

    @Test
    void shouldUseParametersPrefixInLocationIfBundleIsWrapped() {
      final String wrappedLocation =
          "Parameters.parameter[0].resource.entry[1].resource.extension[0]";
      final List<Extension> removed = List.of(extension(UNEXPECTED));
      when(extensionRemover.remove(bundle, Set.of(wrappedLocation))).thenReturn(removed);
      when(orphanedResourceRemover.removeReferencedResourcesIfOrphaned(bundle, removed))
          .thenReturn(List.of(new RemovedEntry("urn:uuid:1234", 2)));

      final var newIssues =
          executeTestAndGetAddedIssues(unexpectedExtensionOutcome(wrappedLocation));

      assertThat(newIssues)
          .hasSize(2)
          .satisfiesExactlyInAnyOrder(
              issue -> assertExtensionWarning(issue, 1),
              issue ->
                  assertResourceWarning(
                      issue, "urn:uuid:1234", "Parameters.parameter[0].resource.entry[2]"));
    }

    @Test
    void shouldAddSingleWarningIfMultipleExtensionsWereRemoved() {
      final List<Extension> removed = List.of(extension(UNEXPECTED), extension(UNEXPECTED));
      when(extensionRemover.remove(bundle, Set.of(LOCATION_0, LOCATION_1))).thenReturn(removed);
      when(orphanedResourceRemover.removeReferencedResourcesIfOrphaned(bundle, removed))
          .thenReturn(List.of());

      final var newIssues =
          executeTestAndGetAddedIssues(unexpectedExtensionOutcome(LOCATION_0, LOCATION_1));

      assertThat(newIssues)
          .as("a single warning, although two extensions were removed")
          .singleElement()
          .satisfies(issue -> assertExtensionWarning(issue, 2));
    }

    @Test
    void shouldNotAddWarningIfNothingWasRemoved() {
      when(extensionRemover.remove(any(), any())).thenReturn(List.of());

      final var newIssues = executeTestAndGetAddedIssues(unexpectedExtensionOutcome(LOCATION_0));

      assertThat(newIssues).isEmpty();
      verifyNoInteractions(orphanedResourceRemover);
    }
  }

  @Nested
  class ErrorHandling {

    @Test
    void shouldCatchExceptionOfRemover() {
      when(extensionRemover.remove(any(), any())).thenThrow(new IllegalStateException("test"));

      final OperationOutcome outcome = unexpectedExtensionOutcome(LOCATION_0);
      assertThatCode(() -> underTest.cleanupUnexpectedExtensions(bundle, outcome))
          .doesNotThrowAnyException();
    }

    @Test
    void shouldNotRemoveOrphanedResourcesIfExtensionRemovalFails() {
      when(extensionRemover.remove(any(), any())).thenThrow(new IllegalStateException("test"));

      underTest.cleanupUnexpectedExtensions(bundle, unexpectedExtensionOutcome(LOCATION_0));

      verifyNoInteractions(orphanedResourceRemover);
    }

    @Test
    void shouldCatchExceptionOfOrphanedResourceRemover() {
      when(extensionRemover.remove(any(), any())).thenReturn(List.of(extension(UNEXPECTED)));
      doThrow(new IllegalStateException("test"))
          .when(orphanedResourceRemover)
          .removeReferencedResourcesIfOrphaned(any(), any());

      final OperationOutcome outcome = unexpectedExtensionOutcome(LOCATION_0);
      assertThatCode(() -> underTest.cleanupUnexpectedExtensions(bundle, outcome))
          .doesNotThrowAnyException();
    }
  }
}
