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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.stereotype.Component;

@Component
@Slf4j
class OrphanedResourceRemover {

  /**
   * Removes bundle entries that became orphaned by the removal of the given extensions.
   *
   * <p>Candidates are the resources referenced by the removed extensions. A candidate is removed
   * together with everything that is only reachable through it, unless it is still reachable from
   * the composition. The extensions must already be detached from the bundle, otherwise their
   * targets still count as reachable.
   *
   * @return the removed bundle entries, in bundle order
   */
  List<RemovedEntry> removeReferencedResourcesIfOrphaned(
      final Bundle bundle, final List<Extension> removedExtensions) {
    final Set<Resource> candidates = getReferencedResources(removedExtensions);
    if (candidates.isEmpty()) {
      return List.of();
    }

    final Set<Resource> reachableResources =
        collectTransitivelyReferencedResources(getComposition(bundle));

    final Set<Resource> toRemove = determineOrphanedResources(reachableResources, candidates);
    return removeEntries(bundle, toRemove);
  }

  private List<RemovedEntry> removeEntries(final Bundle bundle, final Set<Resource> toRemove) {
    final List<RemovedEntry> removed = new ArrayList<>();
    final Iterator<BundleEntryComponent> entries = bundle.getEntry().iterator();
    int originalIndex = 0;
    while (entries.hasNext()) {
      final BundleEntryComponent entry = entries.next();
      if (toRemove.contains(entry.getResource())) {
        entries.remove();
        logRemovedResource(originalIndex, entry);
        removed.add(new RemovedEntry(entry.getFullUrl(), originalIndex));
      }
      originalIndex++;
    }
    return removed;
  }

  private void logRemovedResource(final int originalIndex, final BundleEntryComponent entry) {
    log.info(
        "Removed orphaned resource {\"bundle-entry-index\":{}, \"type\":\"{}\", \"url\":\"{}\"}",
        originalIndex,
        entry.getResource().getResourceType(),
        entry.getFullUrl());
  }

  private Set<Resource> collectTransitivelyReferencedResources(final Resource root) {
    final Set<Resource> reachable = Collections.newSetFromMap(new IdentityHashMap<>());
    final Deque<Resource> toVisit = new ArrayDeque<>();
    toVisit.push(root);
    while (!toVisit.isEmpty()) {
      final Resource resource = toVisit.pop();
      final boolean isNewResource = reachable.add(resource);
      if (isNewResource) {
        toVisit.addAll(ReferenceCollector.collectAllReferencedResources(resource));
      }
    }
    return reachable;
  }

  private Set<Resource> determineOrphanedResources(
      final Set<Resource> reachableResources, final Set<Resource> candidates) {
    final Set<Resource> orphaned = Collections.newSetFromMap(new IdentityHashMap<>());
    final Deque<Resource> toVisit = new ArrayDeque<>(candidates);
    while (!toVisit.isEmpty()) {
      final Resource resource = toVisit.pop();
      if (!reachableResources.contains(resource) && orphaned.add(resource)) {
        toVisit.addAll(ReferenceCollector.collectAllReferencedResources(resource));
      }
    }
    return orphaned;
  }

  private Composition getComposition(final Bundle bundle) {
    return bundle.getEntry().stream()
        .map(BundleEntryComponent::getResource)
        .filter(Composition.class::isInstance)
        .map(Composition.class::cast)
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Bundle contains no Composition"));
  }

  private Set<Resource> getReferencedResources(final List<Extension> extensions) {
    return extensions.stream()
        .map(ReferenceCollector::collectAllReferencedResources)
        .flatMap(Set::stream)
        .collect(Collectors.toCollection(() -> Collections.newSetFromMap(new IdentityHashMap<>())));
  }

  /**
   * A bundle entry that was removed.
   *
   * @param fullUrl the {@code fullUrl} of the entry, may be {@code null}
   * @param index the index of the entry in the bundle before any entry was removed
   */
  record RemovedEntry(String fullUrl, int index) {}
}
