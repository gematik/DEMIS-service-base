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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import java.util.Date;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ReferenceCollectorTest {

  @Test
  void nullRootThrowsNPE() {
    assertThatThrownBy(() -> ReferenceCollector.collectAllReferencedResources(null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void rootWithoutReferencesYieldsEmptySet() {
    assertThat(ReferenceCollector.collectAllReferencedResources(new Patient())).isEmpty();
  }

  @Test
  void collectsReferenceOfRootItself() {
    final Organization target = new Organization();

    assertThat(ReferenceCollector.collectAllReferencedResources(new Reference(target)))
        .containsExactly(target);
  }

  @Test
  void collectsReferenceInRegularField() {
    final Organization target = new Organization();
    final Patient patient = new Patient();
    patient.setManagingOrganization(new Reference(target));

    assertThat(ReferenceCollector.collectAllReferencedResources(patient)).containsExactly(target);
  }

  @Test
  void collectsReferenceInExtension() {
    final Organization target = new Organization();
    final Extension extension = new Extension("urn:test", new Reference(target));

    assertThat(ReferenceCollector.collectAllReferencedResources(extension)).containsExactly(target);
  }

  @Test
  void collectsReferenceInNestedExtension() {
    final Organization target = new Organization();
    final Extension outer = new Extension("urn:outer");
    outer.addExtension(new Extension("urn:inner", new Reference(target)));

    assertThat(ReferenceCollector.collectAllReferencedResources(outer)).containsExactly(target);
  }

  @Test
  void collectsReferenceInExtensionOfPrimitiveElement() {
    final Organization target = new Organization();
    final Patient patient = new Patient();
    patient.setBirthDate(new Date());
    patient.getBirthDateElement().addExtension("urn:test", new Reference(target));

    assertThat(ReferenceCollector.collectAllReferencedResources(patient)).containsExactly(target);
  }

  @Test
  void collectsReferenceInContainedResource() {
    final Organization target = new Organization();
    final Observation contained = new Observation();
    contained.addExtension("urn:test", new Reference(target));
    final Patient patient = new Patient();
    patient.addContained(contained);

    assertThat(ReferenceCollector.collectAllReferencedResources(patient)).containsExactly(target);
  }

  @Test
  void collectsAllDistinctTargets() {
    final Organization first = new Organization();
    final Practitioner second = new Practitioner();
    final Patient patient = new Patient();
    patient.addExtension("urn:a", new Reference(first));
    patient.addExtension("urn:b", new Reference(second));
    patient.addExtension("urn:c", new Reference(first));

    assertThat(ReferenceCollector.collectAllReferencedResources(patient))
        .containsExactlyInAnyOrder(first, second);
  }

  @Test
  void ignoresReferenceWithoutResolvedResource() {
    final Patient patient = new Patient();
    patient.addExtension("urn:test", new Reference("Organization/123"));

    assertThat(ReferenceCollector.collectAllReferencedResources(patient)).isEmpty();
  }

  @Test
  void doesNotFollowResolvedReferences() {
    final Practitioner far = new Practitioner();
    final Organization near = new Organization();
    near.addExtension("urn:test", new Reference(far));
    final Patient patient = new Patient();
    patient.addExtension("urn:test", new Reference(near));

    assertThat(ReferenceCollector.collectAllReferencedResources(patient)).containsExactly(near);
  }

  @Nested
  class Parser {

    private static final FhirContext FHIR_CONTEXT = FhirContext.forR4Cached();

    private static final String ID = "02D9FF61-D6B4-FDC5-8FD7-22C49F5B8A9E";

    // {id} is replaced by ID, empty columns mean: the patient entry has no fullUrl / no id
    private static final String NOTATIONS =
        """
        urn:uuid:{id}                          |        | urn:uuid:{id}
        https://example.org/fhir/Patient/{id}  | {id}   | Patient/{id}
        urn:uuid:{id}                          | {id}   | Patient/{id}
                                               | {id}   | Patient/{id}
        https://example.org/fhir/Patient/{id}  | {id}   | https://example.org/fhir/Patient/{id}
                                               | {id}   | https://example.org/fhir/Patient/{id}
        https://example.org/fhir/Patient/{id}  | {id}   | Patient/{id}/_history/1
        """;

    private static String expand(final String value) {
      return value == null ? null : value.replace("{id}", ID);
    }

    private static String json(final String fullUrl, final String id, final String reference) {
      return """
      {
        "resourceType": "Bundle",
        "type": "document",
        "entry": [
          {
            "fullUrl": "urn:uuid:00000000-0000-0000-0000-000000000001",
            "resource": {
              "resourceType": "Composition",
              "extension": [
                { "url": "urn:test", "valueReference": { "reference": "%s" } }
              ]
            }
          },
          {
            %s
            "resource": { "resourceType": "Patient"%s }
          }
        ]
      }
      """
          .formatted(
              reference,
              fullUrl == null ? "" : "\"fullUrl\": \"%s\",".formatted(fullUrl),
              id == null ? "" : ", \"id\": \"%s\"".formatted(id));
    }

    private static String xml(final String fullUrl, final String id, final String reference) {
      return """
      <Bundle xmlns="http://hl7.org/fhir">
        <type value="document"/>
        <entry>
          <fullUrl value="urn:uuid:00000000-0000-0000-0000-000000000001"/>
          <resource>
            <Composition>
              <extension url="urn:test">
                <valueReference><reference value="%s"/></valueReference>
              </extension>
            </Composition>
          </resource>
        </entry>
        <entry>
          %s
          <resource>
            <Patient>%s</Patient>
          </resource>
        </entry>
      </Bundle>
      """
          .formatted(
              reference,
              fullUrl == null ? "" : "<fullUrl value=\"%s\"/>".formatted(fullUrl),
              id == null ? "" : "<id value=\"%s\"/>".formatted(id));
    }

    private static void assertParserResolvesReference(
        final IParser parser, final String encodedBundle) {
      final Bundle bundle = parser.parseResource(Bundle.class, encodedBundle);
      final Resource composition = bundle.getEntry().get(0).getResource();
      final Resource patient = bundle.getEntry().get(1).getResource();

      assertThat(ReferenceCollector.collectAllReferencedResources(composition))
          .containsExactly(patient);
    }

    /**
     * The remover relies on the parser to resolve references between bundle entries. Documents come
     * from outside, so the different notations of a reference have to be covered for JSON and XML.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = NOTATIONS)
    void parserResolvesReferencesInJson(
        final String fullUrl, final String id, final String reference) {
      assertParserResolvesReference(
          FHIR_CONTEXT.newJsonParser(), json(expand(fullUrl), expand(id), expand(reference)));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = NOTATIONS)
    void parserResolvesReferencesInXml(
        final String fullUrl, final String id, final String reference) {
      assertParserResolvesReference(
          FHIR_CONTEXT.newXmlParser(), xml(expand(fullUrl), expand(id), expand(reference)));
    }
  }
}
