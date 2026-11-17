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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import de.gematik.demis.service.base.fhir.cleanup.OrphanedResourceRemover.RemovedEntry;
import java.util.List;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class OrphanedResourceRemoverTest {

  private final OrphanedResourceRemover underTest = new OrphanedResourceRemover();

  private final Composition composition = new Composition();

  private static List<Resource> resourcesOf(final Bundle bundle) {
    return bundle.getEntry().stream().map(Bundle.BundleEntryComponent::getResource).toList();
  }

  private static <T extends Resource> T linkedFrom(final DomainResource source, final T target) {
    source.addExtension("urn:test:link", new Reference(target));
    return target;
  }

  private static Extension removedExtensionTo(final Resource target) {
    return new Extension("urn:test:removed", new Reference(target));
  }

  private static String fullUrlAt(final int index) {
    return "urn:test:" + index;
  }

  private static RemovedEntry removedEntryAt(final int index) {
    return new RemovedEntry(fullUrlAt(index), index);
  }

  // The composition is always entry 0, the given resources follow with their position as index.
  private Bundle bundleOf(final Resource... resources) {
    final Bundle bundle = new Bundle();
    bundle.addEntry().setFullUrl(fullUrlAt(0)).setResource(composition);
    for (final Resource resource : resources) {
      final int index = bundle.getEntry().size();
      bundle.addEntry().setFullUrl(fullUrlAt(index)).setResource(resource);
    }
    return bundle;
  }

  @Nested
  @DisplayName("Nothing to remove")
  class NothingToRemove {

    @Test
    void noExtensionWasRemoved() {
      final Patient patient = linkedFrom(composition, new Patient());
      final Bundle bundle = bundleOf(patient);

      final List<RemovedEntry> result =
          underTest.removeReferencedResourcesIfOrphaned(bundle, List.of());

      assertThat(resourcesOf(bundle)).containsExactly(composition, patient);
      assertThat(result).isEmpty();
    }

    @Test
    void removedExtensionHasNoReference() {
      final Patient patient = linkedFrom(composition, new Patient());
      final Bundle bundle = bundleOf(patient);
      final List<Extension> removedExtensionWithoutReference =
          List.of(new Extension("urn:test", new StringType("x")));

      final List<RemovedEntry> result =
          underTest.removeReferencedResourcesIfOrphaned(bundle, removedExtensionWithoutReference);

      assertThat(resourcesOf(bundle)).containsExactly(composition, patient);
      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("Referenced resource is orphaned")
  class ResourceIsRemoved {

    @Test
    void onlyReferencedByRemovedExtension() {
      final Patient orphan = new Patient();
      final Patient kept = linkedFrom(composition, new Patient());
      final Bundle bundle = bundleOf(kept, orphan);

      final List<RemovedEntry> result =
          underTest.removeReferencedResourcesIfOrphaned(
              bundle, List.of(removedExtensionTo(orphan)));

      assertThat(resourcesOf(bundle)).containsExactly(composition, kept);
      assertThat(result).containsExactly(removedEntryAt(2));
    }

    @Test
    void shouldLogEachRemovedResourceWithIndexTypeAndUrl() {
      final Patient orphan = new Patient();
      final Organization secondOrphan = new Organization();
      final Bundle bundle = bundleOf(orphan, secondOrphan);
      final Logger logger = (Logger) LoggerFactory.getLogger(OrphanedResourceRemover.class);
      final ListAppender<ILoggingEvent> logAppender = new ListAppender<>();
      logAppender.start();
      logger.addAppender(logAppender);

      try {
        underTest.removeReferencedResourcesIfOrphaned(
            bundle, List.of(removedExtensionTo(orphan), removedExtensionTo(secondOrphan)));
      } finally {
        logger.detachAppender(logAppender);
        logAppender.stop();
      }

      assertThat(logAppender.list)
          .filteredOn(event -> event.getLevel() == Level.INFO)
          .extracting(ILoggingEvent::getFormattedMessage)
          .containsExactlyInAnyOrder(
              "Removed orphaned resource {\"bundle-entry-index\":1, \"type\":\"Patient\", \"url\":\"%s\"}"
                  .formatted(fullUrlAt(1)),
              "Removed orphaned resource {\"bundle-entry-index\":2, \"type\":\"Organization\", \"url\":\"%s\"}"
                  .formatted(fullUrlAt(2)));
    }

    @Test
    void referencedBySeveralRemovedExtensions() {
      final Patient orphan = new Patient();
      final Bundle bundle = bundleOf(orphan);

      final List<RemovedEntry> result =
          underTest.removeReferencedResourcesIfOrphaned(
              bundle, List.of(removedExtensionTo(orphan), removedExtensionTo(orphan)));

      assertThat(resourcesOf(bundle)).containsExactly(composition);
      assertThat(result).containsExactly(removedEntryAt(1));
    }

    @Test
    void referencedByNestedExtensionOfRemovedExtension() {
      final Patient orphan = new Patient();
      final Extension outer = new Extension("urn:outer");
      outer.addExtension(removedExtensionTo(orphan));
      final Bundle bundle = bundleOf(orphan);

      final List<RemovedEntry> result =
          underTest.removeReferencedResourcesIfOrphaned(bundle, List.of(outer));

      assertThat(resourcesOf(bundle)).containsExactly(composition);
      assertThat(result).containsExactly(removedEntryAt(1));
    }

    @Test
    void reportsIndexesOfTheBundleBeforeAnyRemoval() {
      final Patient firstOrphan = new Patient();
      final Patient kept = linkedFrom(composition, new Patient());
      final Patient secondOrphan = new Patient();
      final Bundle bundle = bundleOf(firstOrphan, kept, secondOrphan);

      final List<RemovedEntry> result =
          underTest.removeReferencedResourcesIfOrphaned(
              bundle, List.of(removedExtensionTo(firstOrphan), removedExtensionTo(secondOrphan)));

      assertThat(resourcesOf(bundle)).containsExactly(composition, kept);
      assertThat(result).containsExactly(removedEntryAt(1), removedEntryAt(3));
    }

    @Test
    void unrelatedResourcesStayUntouched() {
      final Patient orphan = new Patient();
      final Practitioner unrelated = new Practitioner();
      final Bundle bundle = bundleOf(orphan, unrelated);

      final List<RemovedEntry> result =
          underTest.removeReferencedResourcesIfOrphaned(
              bundle, List.of(removedExtensionTo(orphan)));

      assertThat(resourcesOf(bundle)).containsExactly(composition, unrelated);
      assertThat(result).containsExactly(removedEntryAt(1));
    }
  }

  @Nested
  @DisplayName("Referenced resource is still reachable from the composition")
  class ResourceIsKept {

    @Test
    void directlyReferencedByComposition() {
      final Patient patient = linkedFrom(composition, new Patient());
      final Bundle bundle = bundleOf(patient);

      final List<RemovedEntry> result =
          underTest.removeReferencedResourcesIfOrphaned(
              bundle, List.of(removedExtensionTo(patient)));

      assertThat(resourcesOf(bundle)).containsExactly(composition, patient);
      assertThat(result).isEmpty();
    }

    @Test
    void reachableViaAnotherResource() {
      final Patient patient = linkedFrom(composition, new Patient());
      final Organization organization = linkedFrom(patient, new Organization());
      final Bundle bundle = bundleOf(patient, organization);

      final List<RemovedEntry> result =
          underTest.removeReferencedResourcesIfOrphaned(
              bundle, List.of(removedExtensionTo(organization)));

      assertThat(resourcesOf(bundle)).containsExactly(composition, patient, organization);
      assertThat(result).isEmpty();
    }

    @Test
    void referencedResourceOfOrphanIsSharedWithComposition() {
      final Organization shared = linkedFrom(composition, new Organization());
      final Patient candidate = new Patient();
      linkedFrom(candidate, shared);
      final Bundle bundle = bundleOf(shared, candidate);

      final List<RemovedEntry> result =
          underTest.removeReferencedResourcesIfOrphaned(
              bundle, List.of(removedExtensionTo(candidate)));

      assertThat(resourcesOf(bundle)).containsExactly(composition, shared);
      assertThat(result).containsExactly(removedEntryAt(2));
    }
  }

  @Nested
  @DisplayName("Transitive references")
  class TransitiveReferences {

    @Test
    void removesWholeChainNotReachableFromComposition() {
      final Patient candidate = new Patient();
      final Organization second = linkedFrom(candidate, new Organization());
      final Practitioner third = linkedFrom(second, new Practitioner());
      final Patient kept = linkedFrom(composition, new Patient());
      final Bundle bundle = bundleOf(kept, candidate, second, third);

      final List<RemovedEntry> result =
          underTest.removeReferencedResourcesIfOrphaned(
              bundle, List.of(removedExtensionTo(candidate)));

      assertThat(resourcesOf(bundle)).containsExactly(composition, kept);
      assertThat(result)
          .containsExactlyInAnyOrder(removedEntryAt(2), removedEntryAt(3), removedEntryAt(4));
    }

    @Test
    void removesResourcesThatOnlyReferenceEachOtherInCycle() {
      final Patient candidate = new Patient();
      final Organization second = linkedFrom(candidate, new Organization());
      linkedFrom(second, candidate);
      final Bundle bundle = bundleOf(candidate, second);

      final List<RemovedEntry> result =
          underTest.removeReferencedResourcesIfOrphaned(
              bundle, List.of(removedExtensionTo(candidate)));

      assertThat(resourcesOf(bundle)).containsExactly(composition);
      assertThat(result).containsExactlyInAnyOrder(removedEntryAt(1), removedEntryAt(2));
    }
  }
}
