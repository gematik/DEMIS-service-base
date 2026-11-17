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

import java.util.Arrays;
import java.util.List;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Bundle.BundleType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StringType;

/** Test data builders for the unexpected extension removal. */
final class ExtensionTestFixtures {

  static final String MESSAGE_ID_SYSTEM = "http://hl7.org/fhir/java-core-messageId";
  static final String UNEXPECTED = "http://hl7.org/fhir/StructureDefinition/data-absent-reason";
  static final String UNEXPECTED_2 = "http://hl7.org/fhir/StructureDefinition/translation";
  static final String ALLOWED = "https://demis.rki.de/fhir/StructureDefinition/AllowedExtension";

  static final String COMPOSITION_ID = "89069D8A-4FED-4BA5-8E82-F99B04BDCD29";
  static final String PATIENT_ID = "2DA21025-AF83-3A4C-028A-F7118F7E2118";
  static final String OBSERVATION_ID = "4A4306EA-F9CA-4081-964E-5BA029BAEA9E";

  private ExtensionTestFixtures() {}

  /** Creates a bundle with the composition as entry[0] and the patient as entry[1]. */
  static Bundle bundle(final Composition composition, final Patient patient) {
    return bundleOf(composition, patient);
  }

  /** Creates a bundle with a default composition as entry[0] and the patient as entry[1]. */
  static Bundle bundleWithPatient(final Patient patient) {
    final Composition composition = composition();
    composition.setSubjectTarget(patient);
    return bundle(composition, patient);
  }

  /** Creates a document bundle containing the given resources in the given order. */
  static Bundle bundleOf(final Resource... resources) {
    final Bundle bundle = new Bundle();
    bundle.setType(BundleType.DOCUMENT);
    Arrays.stream(resources).forEach(resource -> addEntry(bundle, resource));
    return bundle;
  }

  static Composition composition() {
    final Composition composition = new Composition();
    composition.setId(COMPOSITION_ID);
    composition.setTitle("Laboratory Report");
    composition.addSection().setTitle("Report");
    return composition;
  }

  static Patient patient() {
    final Patient patient = new Patient();
    patient.setId(PATIENT_ID);
    patient.setBirthDateElement(new DateType("1969-02-03"));
    patient.addIdentifier().setSystem("urn:test").setValue("1");
    return patient;
  }

  /** An observation whose value is a CodeableConcept with one coding. */
  static Observation observation() {
    final Observation observation = new Observation();
    observation.setId(OBSERVATION_ID);
    observation.setValue(
        new CodeableConcept().addCoding(new Coding("http://snomed.info/sct", "10828004", "Pos")));
    return observation;
  }

  static Extension extension(final String url) {
    return new Extension(url, new StringType("value of " + url));
  }

  /** Creates an extension whose value is a reference. */
  static Extension referenceExtension(final String url, final Reference reference) {
    return new Extension(url, reference);
  }

  static List<String> extensionUrls(final List<Extension> extensions) {
    return extensions.stream().map(Extension::getUrl).toList();
  }

  /** Creates an outcome containing one {@code Unexpected_Extension} issue per given location. */
  static OperationOutcome unexpectedExtensionOutcome(final String... locations) {
    final OperationOutcome outcome = new OperationOutcome();
    Arrays.stream(locations).forEach(location -> addUnexpectedExtensionIssue(outcome, location));
    return outcome;
  }

  static void addUnexpectedExtensionIssue(final OperationOutcome outcome, final String location) {
    addIssue(outcome, "Unexpected_Extension", location);
  }

  static void addIssue(
      final OperationOutcome outcome, final String messageId, final String location) {
    final OperationOutcome.OperationOutcomeIssueComponent issue =
        outcome
            .addIssue()
            .setSeverity(IssueSeverity.WARNING)
            .setCode(IssueType.PROCESSING)
            .setDetails(
                new CodeableConcept()
                    .addCoding(new Coding().setSystem(MESSAGE_ID_SYSTEM).setCode(messageId)))
            .setDiagnostics("Es wurde eine unerwartete Extension gefunden");
    if (location != null) {
      issue.addLocation(location);
    }
  }

  static void addEntry(final Bundle bundle, final Resource resource) {
    final BundleEntryComponent entry = bundle.addEntry();
    entry.setFullUrl(
        "https://demis.rki.de/fhir/ars/" + resource.fhirType() + "/" + resource.getId());
    entry.setResource(resource);
  }
}
