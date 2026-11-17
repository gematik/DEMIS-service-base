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

import static java.util.Collections.newSetFromMap;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.Property;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;

/**
 * Collects all {@link Reference}s that are contained in an element or in one of its descendants.
 *
 * <p>The element tree is walked via {@link Base#children()}. In contrast to {@code
 * FhirTerser.visit} this also reaches the extensions of primitive elements (e.g. {@code
 * Patient.birthDate.extension}). Nested resources (contained resources) are walked as well, a
 * {@link Reference} that is already resolved to another resource is <em>not</em> followed.
 */
final class ReferenceCollector {

  private ReferenceCollector() {
    // no instances
  }

  /**
   * @param root the element to search in, e.g. a resource or an extension
   * @return all references found in {@code root} (including {@code root} itself), never {@code
   *     null}
   * @throws NullPointerException if root is null
   */
  static Set<Resource> collectAllReferencedResources(final Base root) {
    final Set<Resource> referencedResources = newSetFromMap(new IdentityHashMap<>());

    final Deque<Base> toVisit = new ArrayDeque<>();
    toVisit.push(root);
    while (!toVisit.isEmpty()) {
      final Base currentElement = toVisit.pop();

      if (currentElement instanceof Reference ref
          && ref.getResource() instanceof Resource resource) {
        referencedResources.add(resource);
      }

      pushChildren(currentElement, toVisit);
    }
    return referencedResources;
  }

  private static void pushChildren(final Base element, final Deque<Base> toVisit) {
    element.children().stream()
        .filter(Property::hasValues)
        .flatMap(property -> property.getValues().stream())
        .filter(Objects::nonNull)
        .forEach(toVisit::push);
  }
}
