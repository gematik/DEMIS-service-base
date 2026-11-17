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
import static de.gematik.demis.service.base.fhir.cleanup.ExtensionTestFixtures.OBSERVATION_ID;
import static de.gematik.demis.service.base.fhir.cleanup.ExtensionTestFixtures.UNEXPECTED;
import static de.gematik.demis.service.base.fhir.cleanup.ExtensionTestFixtures.UNEXPECTED_2;
import static de.gematik.demis.service.base.fhir.cleanup.ExtensionTestFixtures.bundleOf;
import static de.gematik.demis.service.base.fhir.cleanup.ExtensionTestFixtures.bundleWithPatient;
import static de.gematik.demis.service.base.fhir.cleanup.ExtensionTestFixtures.composition;
import static de.gematik.demis.service.base.fhir.cleanup.ExtensionTestFixtures.extension;
import static de.gematik.demis.service.base.fhir.cleanup.ExtensionTestFixtures.observation;
import static de.gematik.demis.service.base.fhir.cleanup.ExtensionTestFixtures.patient;
import static org.assertj.core.api.Assertions.assertThat;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import de.gematik.demis.service.base.fhir.cleanup.ExtensionResolver.ExtensionWithParent;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ExtensionResolverTest {

  private final ExtensionResolver underTest = new ExtensionResolver();

  private void executeTest(
      final Bundle bundle,
      final String location,
      final Base expectedParent,
      final Base expectedExtension) {
    final List<ExtensionWithParent> result = underTest.resolve(bundle, Set.of(location));

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().parent()).isSameAs(expectedParent);
    assertThat(result.getFirst().extension()).isSameAs(expectedExtension);
  }

  @Test
  void shouldNotModifyBundle() {
    final FhirContext fhirContext = FhirContext.forR4Cached();
    final IParser jsonParser = fhirContext.newJsonParser();

    final Patient patient = patient();
    patient.addExtension(extension(UNEXPECTED));
    patient.getBirthDateElement().addExtension(extension(UNEXPECTED_2));
    final Bundle bundle = bundleWithPatient(patient);

    final String before = jsonParser.encodeResourceToString(bundle);

    underTest.resolve(
        bundle,
        Set.of(
            "Bundle.entry[1].resource.extension[0]",
            "Bundle.entry[1].resource.birthDate.extension[0]"));

    assertThat(jsonParser.encodeResourceToString(bundle)).isEqualTo(before);
  }

  @Nested
  @DisplayName("resolves the extension and the element holding it")
  class Resolves {

    @Test
    @DisplayName("on resource level")
    void shouldResolveOnResourceLevel() {
      final Patient patient = patient();
      final Extension extension = extension(UNEXPECTED);
      patient.addExtension(extension);
      final Bundle bundle = bundleWithPatient(patient);

      executeTest(bundle, "Bundle.entry[1].resource.extension[0]", patient, extension);
    }

    @Test
    @DisplayName("on a primitive element")
    void shouldResolveOnPrimitiveElement() {
      final Patient patient = patient();
      final Extension extension = extension(UNEXPECTED);
      patient.getBirthDateElement().addExtension(extension);
      final Bundle bundle = bundleWithPatient(patient);

      executeTest(
          bundle,
          "Bundle.entry[1].resource.birthDate.extension[0]",
          patient.getBirthDateElement(),
          extension);
    }

    @Test
    @DisplayName("on a primitive element that has no value, only extensions")
    void shouldResolveOnPrimitiveWithoutValue() {
      final Patient patient = patient();
      final DateType birthDate = new DateType();
      final Extension extension = extension(UNEXPECTED);
      birthDate.addExtension(extension);
      patient.setBirthDateElement(birthDate);
      final Bundle bundle = bundleWithPatient(patient);

      executeTest(bundle, "Bundle.entry[1].resource.birthDate.extension[0]", birthDate, extension);
    }

    @Test
    @DisplayName("on a StringType element")
    void shouldResolveOnStringType() {
      final Patient patient = patient();
      patient.getNameFirstRep().setFamily("Mustermann");
      final Extension extension = extension(UNEXPECTED);
      patient.getNameFirstRep().getFamilyElement().addExtension(extension);
      final Bundle bundle = bundleWithPatient(patient);

      executeTest(
          bundle,
          "Bundle.entry[1].resource.name[0].family.extension[0]",
          patient.getNameFirstRep().getFamilyElement(),
          extension);
    }

    @Test
    @DisplayName("on a complex datatype")
    void shouldResolveOnComplexDatatype() {
      final Patient patient = patient();
      final Extension extension = extension(UNEXPECTED);
      patient.getIdentifierFirstRep().addExtension(extension);
      final Bundle bundle = bundleWithPatient(patient);

      executeTest(
          bundle,
          "Bundle.entry[1].resource.identifier[0].extension[0]",
          patient.getIdentifierFirstRep(),
          extension);
    }

    @Test
    @DisplayName("on a backbone element")
    void shouldResolveOnBackboneElement() {
      final Composition composition = composition();
      final Extension extension = extension(UNEXPECTED);
      composition.getSectionFirstRep().addExtension(extension);
      final Bundle bundle = ExtensionTestFixtures.bundle(composition, patient());

      executeTest(
          bundle,
          "Bundle.entry[0].resource.section[0].extension[0]",
          composition.getSectionFirstRep(),
          extension);
    }

    @Test
    @DisplayName("on a bundle entry")
    void shouldResolveOnBundleEntry() {
      final Bundle bundle = bundleWithPatient(patient());
      final Extension extension = extension(UNEXPECTED);
      bundle.getEntry().get(1).addExtension(extension);

      executeTest(bundle, "Bundle.entry[1].extension[0]", bundle.getEntry().get(1), extension);
    }

    @Test
    @DisplayName("on the meta element of a resource")
    void shouldResolveOnMeta() {
      final Patient patient = patient();
      final Extension extension = extension(UNEXPECTED);
      patient.getMeta().addExtension(extension);
      final Bundle bundle = bundleWithPatient(patient);

      executeTest(
          bundle, "Bundle.entry[1].resource.meta.extension[0]", patient.getMeta(), extension);
    }

    @Test
    @DisplayName("on a contained resource")
    void shouldResolveOnContainedResource() {
      final Patient patient = patient();
      final Observation contained = new Observation();
      contained.setId("contained1");
      final Extension extension = extension(UNEXPECTED);
      contained.addExtension(extension);
      patient.addContained(contained);
      final Bundle bundle = bundleWithPatient(patient);

      executeTest(
          bundle, "Bundle.entry[1].resource.contained[0].extension[0]", contained, extension);
    }

    @Test
    @DisplayName("on the second element of a repeating element")
    void shouldResolveOnSecondRepetition() {
      final Patient patient = patient();
      patient.addName().setFamily("First");
      final Extension extension = extension(UNEXPECTED);
      patient.addName().setFamily("Second").addExtension(extension);
      final Bundle bundle = bundleWithPatient(patient);

      executeTest(
          bundle,
          "Bundle.entry[1].resource.name[1].extension[0]",
          patient.getName().get(1),
          extension);
    }

    @Test
    @DisplayName("on a reference element")
    void shouldResolveOnReferenceElement() {
      final Composition composition = composition();
      final Reference reference = composition.getSectionFirstRep().addEntry();
      reference.setReference("Patient/1");
      final Extension extension = extension(UNEXPECTED);
      reference.addExtension(extension);
      final Bundle bundle = ExtensionTestFixtures.bundle(composition, patient());

      executeTest(
          bundle,
          "Bundle.entry[0].resource.section[0].entry[0].extension[0]",
          reference,
          extension);
    }

    @Test
    @DisplayName("nested three levels deep")
    void shouldResolveDeeplyNestedExtension() {
      final Patient patient = patient();
      final Extension level1 = extension(ALLOWED);
      final Extension level2 = extension(ALLOWED);
      final Extension level3 = extension(UNEXPECTED);
      level2.addExtension(level3);
      level1.addExtension(level2);
      patient.addExtension(level1);
      final Bundle bundle = bundleWithPatient(patient);

      executeTest(
          bundle,
          "Bundle.entry[1].resource.extension[0].extension[0].extension[0]",
          level2,
          level3);
    }

    @Test
    @DisplayName("nested inside an extension of a primitive element")
    void shouldResolveNestedExtensionOnPrimitiveElement() {
      final Patient patient = patient();
      final Extension outer = extension(ALLOWED);
      final Extension inner = extension(UNEXPECTED);
      outer.addExtension(inner);
      patient.getBirthDateElement().addExtension(outer);
      final Bundle bundle = bundleWithPatient(patient);

      executeTest(
          bundle, "Bundle.entry[1].resource.birthDate.extension[0].extension[0]", outer, inner);
    }

    @Test
    @DisplayName("on the value of another extension")
    void shouldResolveOnValueOfExtension() {
      final Patient patient = patient();
      final Extension outer = extension(ALLOWED);
      final StringType value = (StringType) outer.getValue();
      final Extension inner = extension(UNEXPECTED);
      value.addExtension(inner);
      patient.addExtension(outer);
      final Bundle bundle = bundleWithPatient(patient);

      executeTest(
          bundle, "Bundle.entry[1].resource.extension[0].value[x].extension[0]", value, inner);
    }
  }

  @Nested
  @DisplayName("choice types")
  class ChoiceTypes {

    @Test
    @DisplayName("resolves an extension on a primitive choice element")
    void shouldResolveOnPrimitiveChoiceElement() {
      final Patient patient = patient();
      final BooleanType deceased = new BooleanType(true);
      final Extension extension = extension(UNEXPECTED);
      deceased.addExtension(extension);
      patient.setDeceased(deceased);
      final Bundle bundle = bundleWithPatient(patient);

      executeTest(bundle, "Bundle.entry[1].resource.deceased[x].extension[0]", deceased, extension);
    }

    @Test
    @DisplayName("resolves an extension on Observation.value[x] holding a StringType")
    void shouldResolveOnObservationValueString() {
      final Observation observation = observation();
      final StringType value = new StringType("positive");
      final Extension extension = extension(UNEXPECTED);
      value.addExtension(extension);
      observation.setValue(value);
      final Bundle bundle = bundleOf(composition(), observation);

      executeTest(bundle, "Bundle.entry[1].resource.value[x].extension[0]", value, extension);
    }

    @Test
    @DisplayName("resolves an extension on Observation.value[x] holding a CodeableConcept")
    void shouldResolveOnObservationValueCodeableConcept() {
      final Observation observation = observation();
      final Extension extension = extension(UNEXPECTED);
      observation.getValueCodeableConcept().addExtension(extension);
      final Bundle bundle = bundleOf(composition(), observation);

      executeTest(
          bundle,
          "Bundle.entry[1].resource.value[x].extension[0]",
          observation.getValueCodeableConcept(),
          extension);
    }

    @Test
    @DisplayName("resolves an extension on a coding of Observation.value[x]")
    void shouldResolveOnCodingOfObservationValue() {
      final Observation observation = observation();
      final Coding coding = observation.getValueCodeableConcept().getCodingFirstRep();
      final Extension extension = extension(UNEXPECTED);
      coding.addExtension(extension);
      final Bundle bundle = bundleOf(composition(), observation);

      executeTest(
          bundle, "Bundle.entry[1].resource.value[x].coding[0].extension[0]", coding, extension);
    }

    @Test
    @DisplayName("resolves the location in the notation of the validator")
    void shouldResolveValidatorNotation() {
      final Observation observation = observation();
      final Extension extension = extension(UNEXPECTED);
      observation.getValueCodeableConcept().addExtension(extension);
      final Bundle bundle = bundleOf(composition(), observation);

      executeTest(
          bundle,
          "Bundle.entry[1].resource/*Observation/" + OBSERVATION_ID + "*/.value[x].extension[0]",
          observation.getValueCodeableConcept(),
          extension);
    }
  }

  @Nested
  @DisplayName("selects the right extension")
  class Selects {

    @Test
    @DisplayName("by an index with more than one digit")
    void shouldSelectByMultiDigitIndex() {
      final Patient patient = patient();
      for (int i = 0; i < 11; i++) {
        patient.addExtension(extension("urn:test:ext:" + i));
      }
      final Bundle bundle = bundleWithPatient(patient);

      final List<ExtensionWithParent> result =
          underTest.resolve(bundle, Set.of("Bundle.entry[1].resource.extension[10]"));

      assertThat(result).hasSize(1);
      assertThat(result.getFirst().extension().getUrl()).isEqualTo("urn:test:ext:10");
    }

    @Test
    @DisplayName("by index, even if several extensions have the same url")
    void shouldSelectByIndexIfUrlsAreEqual() {
      final Patient patient = patient();
      final Extension first = extension(UNEXPECTED);
      final Extension second = extension(UNEXPECTED);
      second.setValue(new StringType("second"));
      patient.addExtension(first);
      patient.addExtension(second);
      final Bundle bundle = bundleWithPatient(patient);

      final List<ExtensionWithParent> result =
          underTest.resolve(bundle, Set.of("Bundle.entry[1].resource.extension[1]"));

      assertThat(result).hasSize(1);
      assertThat(result.getFirst().extension()).isSameAs(second);
    }

    @Test
    @DisplayName("several locations of the same element are all resolved against the same bundle")
    void shouldResolveSeveralLocationsOfSameElement() {
      final Patient patient = patient();
      final Extension first = extension(UNEXPECTED);
      final Extension allowed = extension(ALLOWED);
      final Extension third = extension(UNEXPECTED_2);
      patient.addExtension(first);
      patient.addExtension(allowed);
      patient.addExtension(third);
      final Bundle bundle = bundleWithPatient(patient);

      final List<ExtensionWithParent> result =
          underTest.resolve(
              bundle,
              Set.of(
                  "Bundle.entry[1].resource.extension[2]",
                  "Bundle.entry[1].resource.extension[0]"));

      assertThat(result)
          .extracting(ExtensionWithParent::extension)
          .containsExactlyInAnyOrder(third, first);
    }
  }

  @Nested
  @DisplayName("notations of the validator")
  class Notations {

    @Test
    @DisplayName("understands the Parameters prefix")
    void shouldUnderstandParametersPrefix() {
      final Patient patient = patient();
      patient.addExtension(extension(UNEXPECTED));
      final Bundle bundle = bundleWithPatient(patient);

      final List<ExtensionWithParent> result =
          underTest.resolve(
              bundle, Set.of("Parameters.parameter[0].resource.entry[1].resource.extension[0]"));

      assertThat(result).hasSize(1);
      assertThat(result.getFirst().parent()).isSameAs(patient);
    }
  }

  @Nested
  @DisplayName("ignores locations that cannot be resolved")
  class Ignores {

    private final Patient patient = patient();
    private final Bundle bundle = bundleWithPatient(patient);

    Ignores() {
      patient.addExtension(extension(UNEXPECTED));
    }

    @Test
    @DisplayName("null, empty and blank locations")
    void shouldIgnoreNullAndBlankLocations() {
      final List<ExtensionWithParent> result =
          underTest.resolve(bundle, new LinkedHashSet<>(Arrays.asList(null, "", " ")));

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("an unparsable location")
    void shouldIgnoreUnparsableLocation() {
      assertThat(underTest.resolve(bundle, Set.of("this is not a path"))).isEmpty();
    }

    @Test
    @DisplayName("a location that does not start at the bundle")
    void shouldIgnoreWrongRoot() {
      assertThat(underTest.resolve(bundle, Set.of("Patient.extension[0]"))).isEmpty();
    }

    @Test
    @DisplayName("a location that does not point to an extension")
    void shouldIgnoreLocationNotPointingToAnExtension() {
      assertThat(underTest.resolve(bundle, Set.of("Bundle.entry[1].resource.birthDate"))).isEmpty();
    }

    @Test
    @DisplayName("a location pointing to the extension list without an index")
    void shouldIgnoreLocationWithoutIndex() {
      assertThat(underTest.resolve(bundle, Set.of("Bundle.entry[1].resource.extension"))).isEmpty();
    }

    @Test
    @DisplayName("a modifier extension")
    void shouldIgnoreModifierExtension() {
      patient.addModifierExtension(extension(UNEXPECTED));

      assertThat(underTest.resolve(bundle, Set.of("Bundle.entry[1].resource.modifierExtension[0]")))
          .isEmpty();
    }

    @Test
    @DisplayName("a modifier extension of a nested element")
    void shouldIgnoreModifierExtensionOfNestedElement() {
      final Composition composition = composition();
      composition.getSectionFirstRep().addModifierExtension(extension(UNEXPECTED));
      final Bundle other = ExtensionTestFixtures.bundle(composition, patient());

      assertThat(
              underTest.resolve(
                  other, Set.of("Bundle.entry[0].resource.section[0].modifierExtension[0]")))
          .isEmpty();
    }

    @Test
    @DisplayName("a non existing element")
    void shouldIgnoreUnknownElement() {
      assertThat(
              underTest.resolve(
                  bundle, Set.of("Bundle.entry[1].resource.doesNotExist.extension[0]")))
          .isEmpty();
    }

    @Test
    @DisplayName("an element without value")
    void shouldIgnoreEmptyElement() {
      assertThat(underTest.resolve(bundle, Set.of("Bundle.entry[1].resource.name[0].extension[0]")))
          .isEmpty();
    }

    @Test
    @DisplayName("a non existing entry")
    void shouldIgnoreUnknownEntry() {
      assertThat(underTest.resolve(bundle, Set.of("Bundle.entry[99].resource.extension[0]")))
          .isEmpty();
    }

    @Test
    @DisplayName("an entry without resource")
    void shouldIgnoreEntryWithoutResource() {
      bundle.addEntry().setFullUrl("urn:uuid:no-resource");

      assertThat(underTest.resolve(bundle, Set.of("Bundle.entry[2].resource.extension[0]")))
          .isEmpty();
    }

    @Test
    @DisplayName("an extension index that is out of range")
    void shouldIgnoreIndexOutOfRange() {
      assertThat(underTest.resolve(bundle, Set.of("Bundle.entry[1].resource.extension[7]")))
          .isEmpty();
    }

    @Test
    @DisplayName("the bundle itself, it cannot hold extensions")
    void shouldIgnoreBundleItself() {
      assertThat(underTest.resolve(bundle, Set.of("Bundle.extension[0]"))).isEmpty();
    }

    @Test
    @DisplayName("but still resolves the valid locations in the same call")
    void shouldResolveValidLocationsDespiteBrokenOnes() {
      final List<ExtensionWithParent> result =
          underTest.resolve(bundle, Set.of("<<broken>>", "Bundle.entry[1].resource.extension[0]"));

      assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("an empty location list")
    void shouldReturnNothingForEmptyList() {
      assertThat(underTest.resolve(bundle, Set.of())).isEmpty();
    }
  }
}
