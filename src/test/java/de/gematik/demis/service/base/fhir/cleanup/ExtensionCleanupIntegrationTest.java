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

import static de.gematik.demis.service.base.fhir.cleanup.ExtensionTestFixtures.ALLOWED;
import static de.gematik.demis.service.base.fhir.cleanup.ExtensionTestFixtures.UNEXPECTED;
import static de.gematik.demis.service.base.fhir.cleanup.ExtensionTestFixtures.UNEXPECTED_2;
import static de.gematik.demis.service.base.fhir.cleanup.ExtensionTestFixtures.addIssue;
import static de.gematik.demis.service.base.fhir.cleanup.ExtensionTestFixtures.bundleOf;
import static de.gematik.demis.service.base.fhir.cleanup.ExtensionTestFixtures.bundleWithPatient;
import static de.gematik.demis.service.base.fhir.cleanup.ExtensionTestFixtures.composition;
import static de.gematik.demis.service.base.fhir.cleanup.ExtensionTestFixtures.extension;
import static de.gematik.demis.service.base.fhir.cleanup.ExtensionTestFixtures.extensionUrls;
import static de.gematik.demis.service.base.fhir.cleanup.ExtensionTestFixtures.observation;
import static de.gematik.demis.service.base.fhir.cleanup.ExtensionTestFixtures.patient;
import static de.gematik.demis.service.base.fhir.cleanup.ExtensionTestFixtures.referenceExtension;
import static de.gematik.demis.service.base.fhir.cleanup.ExtensionTestFixtures.unexpectedExtensionOutcome;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.hl7.fhir.r4.model.OperationOutcome.OperationOutcomeIssueComponent;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;

/** Wires the real service, remover and resolver without any mock. */
class ExtensionCleanupIntegrationTest {

  private final ExtensionCleanupService underTest =
      new ExtensionCleanupService(
          new ExtensionRemover(new ExtensionResolver()), new OrphanedResourceRemover());

  private static void assertRemovedExtensionsWarning(
      final OperationOutcome outcome, final int count) {
    assertThat(outcome.getIssue())
        .extracting(
            OperationOutcomeIssueComponent::getSeverity,
            OperationOutcomeIssueComponent::getCode,
            OperationOutcomeIssueComponent::getDiagnostics)
        .contains(
            tuple(
                IssueSeverity.WARNING,
                IssueType.INFORMATIONAL,
                count + " unerwartete Extension(s) wurden entfernt"));
  }

  /** Cases where the service must not change anything, or fails and has to stay harmless. */
  @Nested
  class Common {

    /**
     * The unexpected extension would be removed and the observation orphaned, if it was reported.
     */
    private static Bundle bundleWithRemovableOrphan() {
      final Composition composition = composition();
      final Patient patient = patient();
      final Observation observation = observation();
      composition.setSubject(new Reference(patient));
      patient.addExtension(referenceExtension(UNEXPECTED, new Reference(observation)));
      patient.addExtension(extension(ALLOWED));
      return bundleOf(composition, patient, observation);
    }

    @Test
    void shouldDoNothingIfOutcomeIsEmpty() {
      final Bundle bundle = bundleWithRemovableOrphan();
      final Bundle expected = bundle.copy();
      final OperationOutcome outcome = new OperationOutcome();

      underTest.cleanupUnexpectedExtensions(bundle, outcome);

      assertThat(bundle.equalsDeep(expected)).isTrue();
      assertThat(outcome.getIssue()).isEmpty();
    }

    @Test
    void shouldDoNothingIfOutcomeHasOnlyOtherIssues() {
      final Bundle bundle = bundleWithRemovableOrphan();
      final Bundle expected = bundle.copy();
      final OperationOutcome outcome = new OperationOutcome();
      addIssue(outcome, "Some_Other_MessageId", "Bundle.entry[1].resource.extension[0]");
      final OperationOutcome expectedOutcome = outcome.copy();

      underTest.cleanupUnexpectedExtensions(bundle, outcome);

      assertThat(bundle.equalsDeep(expected)).isTrue();
      assertThat(outcome.equalsDeep(expectedOutcome)).isTrue();
    }

    /** The orphan removal fails (no Composition), the service logs the error and does not throw. */
    @Test
    void shouldLogAndIgnoreOrphanRemovalErrorsAndNotRollBackExtensionRemoval() {
      final Patient patient = patient();
      patient.addExtension(referenceExtension(UNEXPECTED, new Reference(observation())));
      final Bundle bundle = bundleOf(patient);
      final OperationOutcome outcome =
          unexpectedExtensionOutcome("Bundle.entry[0].resource.extension[0]");
      final OperationOutcome expectedOutcome = outcome.copy();
      final Logger logger = (Logger) LoggerFactory.getLogger(ExtensionCleanupService.class);
      final ListAppender<ILoggingEvent> logAppender = new ListAppender<>();
      logAppender.start();
      logger.addAppender(logAppender);

      try {
        underTest.cleanupUnexpectedExtensions(bundle, outcome);
      } finally {
        logger.detachAppender(logAppender);
        logAppender.stop();
      }

      assertThat(logAppender.list).filteredOn(event -> event.getLevel() == Level.ERROR).hasSize(1);
      assertThat(patient.getExtension()).isEmpty();
      assertThat(outcome.getIssue()).hasSize(2);
      assertThat(outcome.getIssue().getFirst().equalsDeep(expectedOutcome.getIssueFirstRep()))
          .isTrue();
      assertRemovedExtensionsWarning(outcome, 1);
    }
  }

  /**
   * Only the reported extensions are removed. Resources are untouched, only the single warning
   * about the removed extensions is added.
   */
  @Nested
  class ExtensionsOnly {

    /** All locations point to the same extension, they only differ in notation. */
    @ParameterizedTest
    @ValueSource(
        strings = {
          "Bundle.entry[1].resource.extension[0]",
          "Bundle.entry[1].resource/*Patient/02D9FF61-D6B4-FDC5-8FD7-22C49F5B8A9E*/.extension[0]",
          "Parameters.parameter.resource.entry[1].resource.extension[0]",
          "Parameters.parameter[0].resource.entry[1].resource.extension[0]",
          "Parameters.parameter[0].resource/*Bundle/null*/.entry[1].resource.extension[0]",
          "Parameters.parameter[0].resource/*Bundle/null*/.entry[1].resource/*Patient/02D9FF61-D6B4-FDC5-8FD7-22C49F5B8A9E*/.extension[0]"
        })
    void shouldRemoveReportedExtensionAndKeepTheRest(final String locationNotation) {
      final Patient patient = patient();
      patient.addExtension(extension(UNEXPECTED));
      patient.addExtension(extension(ALLOWED));
      final Bundle bundle = bundleWithPatient(patient);

      final OperationOutcome outcome = unexpectedExtensionOutcome(locationNotation);

      underTest.cleanupUnexpectedExtensions(bundle, outcome);

      assertThat(extensionUrls(patient.getExtension())).containsExactly(ALLOWED);
      assertRemovedExtensionsWarning(outcome, 1);
    }

    @Test
    void shouldRemoveExtensionOfPrimitiveElement() {
      final Patient patient = new Patient();
      final Bundle bundle = bundleWithPatient(patient);
      // DateType
      patient.setBirthDateElement(new DateType("2000-01-02"));
      patient.getBirthDateElement().addExtension(extension(UNEXPECTED_2));
      // StringType
      patient.addIdentifier().setValue("identifier-value");
      patient.getIdentifierFirstRep().getValueElement().addExtension(extension(UNEXPECTED));

      final OperationOutcome outcome =
          unexpectedExtensionOutcome(
              "Bundle.entry[1].resource.birthDate.extension[0]",
              "Bundle.entry[1].resource.identifier[0].value.extension[0]");

      underTest.cleanupUnexpectedExtensions(bundle, outcome);

      assertRemovedExtensionsWarning(outcome, 2);
      assertThat(patient.getBirthDateElement().getExtension()).isEmpty();
      assertThat(patient.getBirthDateElement().getValueAsString()).isEqualTo("2000-01-02");
      assertThat(patient.getIdentifierFirstRep().getValueElement().getExtension()).isEmpty();
      assertThat(patient.getIdentifierFirstRep().getValue()).isEqualTo("identifier-value");
    }

    @Test
    void shouldRemoveExtensionAndNestedExtensionReportedTogether() {
      final Patient patient = patient();
      final Extension outer = extension(UNEXPECTED);
      outer.addExtension(extension(UNEXPECTED_2));
      patient.addExtension(outer);
      patient.addExtension(extension(ALLOWED));
      final Bundle bundle = bundleWithPatient(patient);

      final OperationOutcome outcome =
          unexpectedExtensionOutcome(
              "Bundle.entry[1].resource.extension[0]",
              "Bundle.entry[1].resource.extension[0].extension[0]");

      underTest.cleanupUnexpectedExtensions(bundle, outcome);

      assertThat(extensionUrls(patient.getExtension())).containsExactly(ALLOWED);
      assertRemovedExtensionsWarning(outcome, 2);
    }

    @Test
    void shouldIgnoreOtherIssuesAndUnresolvableLocations() {
      final Patient patient = patient();
      patient.addExtension(extension(UNEXPECTED));
      patient.addExtension(extension(ALLOWED));
      final Bundle bundle = bundleWithPatient(patient);
      final OperationOutcome outcome =
          unexpectedExtensionOutcome(
              "Bundle.entry[99].resource.extension[0]", "Bundle.entry[1].resource.extension[0]");
      addIssue(outcome, "Some_Other_MessageId", "Bundle.entry[1].resource.extension[1]");
      final Bundle expected = bundle.copy();
      ((Patient) expected.getEntry().get(1).getResource()).getExtension().removeFirst();
      final OperationOutcome expectedOutcome = outcome.copy();

      underTest.cleanupUnexpectedExtensions(bundle, outcome);

      assertThat(extensionUrls(patient.getExtension())).containsExactly(ALLOWED);
      assertThat(bundle.equalsDeep(expected)).as("nothing else was changed in the bundle").isTrue();
      assertThat(outcome.getIssue()).hasSize(expectedOutcome.getIssue().size() + 1);
      assertThat(outcome.getIssue().subList(0, expectedOutcome.getIssue().size()))
          .zipSatisfy(
              expectedOutcome.getIssue(),
              (actual, expectedIssue) -> assertThat(actual.equalsDeep(expectedIssue)).isTrue());
      assertRemovedExtensionsWarning(outcome, 1);
    }
  }

  /**
   * Resources that were only referenced by removed extensions are removed from the bundle as well
   * and reported as warning in the outcome.
   */
  @Nested
  class OrphanedResources {

    private static final String PATIENT_EXTENSION = "Bundle.entry[1].resource.extension[0]";

    private final Composition composition = composition();
    private final Patient patient = patient();
    private final Observation observation = observation();

    private static List<OperationOutcomeIssueComponent> removedResourceWarnings(
        final OperationOutcome outcome) {
      return outcome.getIssue().stream()
          .filter(issue -> issue.getSeverity() == IssueSeverity.WARNING)
          .filter(issue -> issue.getDiagnostics() != null)
          .filter(issue -> issue.getDiagnostics().contains("wurde aus dem Bundle entfernt"))
          .toList();
    }

    private static void assertRemovedResourceIssue(
        final OperationOutcomeIssueComponent issue,
        final String resourceType,
        final String location) {
      assertThat(issue.getCode()).isEqualTo(IssueType.INFORMATIONAL);
      assertThat(issue.getDiagnostics()).contains(resourceType);
      assertThat(issue.getLocation()).extracting(StringType::getValue).containsExactly(location);
    }

    private static List<Resource> resourcesOf(final Bundle bundle) {
      return bundle.getEntry().stream().map(BundleEntryComponent::getResource).toList();
    }

    @BeforeEach
    void linkCompositionToPatient() {
      composition.setSubject(new Reference(patient));
      patient.addExtension(referenceExtension(UNEXPECTED, new Reference(observation)));
    }

    @Test
    void shouldKeepResourcesIfRemovedExtensionHasNoReference() {
      patient.getExtension().clear();
      patient.addExtension(extension(UNEXPECTED));
      final Bundle bundle = bundleOf(composition, patient, observation);
      final OperationOutcome outcome = unexpectedExtensionOutcome(PATIENT_EXTENSION);

      underTest.cleanupUnexpectedExtensions(bundle, outcome);

      assertThat(resourcesOf(bundle)).containsExactly(composition, patient, observation);
      assertThat(patient.getExtension()).isEmpty();
      assertThat(removedResourceWarnings(outcome)).isEmpty();
      assertRemovedExtensionsWarning(outcome, 1);
    }

    @Test
    void shouldKeepResourceThatIsStillReferencedByTheComposition() {
      composition.getSectionFirstRep().addEntry(new Reference(observation));
      final Bundle bundle = bundleOf(composition, patient, observation);
      final OperationOutcome outcome = unexpectedExtensionOutcome(PATIENT_EXTENSION);

      underTest.cleanupUnexpectedExtensions(bundle, outcome);

      assertThat(resourcesOf(bundle)).containsExactly(composition, patient, observation);
      assertThat(patient.getExtension()).isEmpty();
      assertThat(removedResourceWarnings(outcome)).isEmpty();
      assertRemovedExtensionsWarning(outcome, 1);
    }

    @Test
    void shouldRemoveResourceThatWasOnlyReferencedByRemovedExtension() {
      final Bundle bundle = bundleOf(composition, patient, observation);
      final OperationOutcome outcome = unexpectedExtensionOutcome(PATIENT_EXTENSION);

      underTest.cleanupUnexpectedExtensions(bundle, outcome);

      assertThat(resourcesOf(bundle)).containsExactly(composition, patient);
      assertThat(patient.getExtension()).isEmpty();
      assertRemovedExtensionsWarning(outcome, 1);
      assertThat(removedResourceWarnings(outcome))
          .singleElement()
          .satisfies(issue -> assertRemovedResourceIssue(issue, "Observation", "Bundle.entry[2]"));
    }

    @Test
    void shouldRemoveWholeIslandBehindTheRemovedReference() {
      final Practitioner practitioner = new Practitioner();
      observation.addPerformer(new Reference(practitioner));
      final Bundle bundle = bundleOf(composition, patient, observation, practitioner);
      final OperationOutcome outcome = unexpectedExtensionOutcome(PATIENT_EXTENSION);

      underTest.cleanupUnexpectedExtensions(bundle, outcome);

      assertThat(resourcesOf(bundle)).containsExactly(composition, patient);
      assertRemovedExtensionsWarning(outcome, 1);
      assertThat(removedResourceWarnings(outcome))
          .hasSize(2)
          .satisfiesExactlyInAnyOrder(
              issue -> assertRemovedResourceIssue(issue, "Observation", "Bundle.entry[2]"),
              issue -> assertRemovedResourceIssue(issue, "Practitioner", "Bundle.entry[3]"));
    }
  }
}
