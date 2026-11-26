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

import de.gematik.demis.service.base.fhir.cleanup.ExtensionResolver.ExtensionWithParent;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.instance.model.api.IBaseHasExtensions;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Extension;
import org.springframework.stereotype.Component;

/**
 * Removes the extensions addressed by validation locations from a bundle.
 *
 * <p>Works in two phases: first all locations are resolved against the untouched bundle by the
 * {@link ExtensionResolver}, then the resolved extensions are removed by object identity. Removing
 * several extensions of the same element can therefore not run into index shifting problems, and a
 * duplicated location is removed only once.
 *
 * <p>Only {@link IBaseHasExtensions#getExtension()} is touched, modifier extensions are never
 * removed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class ExtensionRemover {

  private final ExtensionResolver extensionResolver;

  /**
   * @return {@code false} if the extension is already gone, e.g. because of a duplicated location
   */
  private static boolean removeFromParent(final ExtensionWithParent extensionWithParent) {
    return extensionWithParent
        .parent()
        .getExtension()
        .removeIf(candidate -> candidate == extensionWithParent.extension());
  }

  /**
   * @param bundle the bundle to clean, is modified in place
   * @param locations the validation issue locations pointing to the unexpected extensions
   * @return the extensions that were actually removed from the bundle
   */
  List<Extension> remove(final Bundle bundle, final Set<String> locations) {
    // phase 1: resolve everything against the untouched bundle
    final List<ExtensionWithParent> extensions = extensionResolver.resolve(bundle, locations);

    // phase 2: remove by identity, so the indexes resolved in phase 1 cannot shift
    final List<Extension> removed = new ArrayList<>(extensions.size());
    for (final ExtensionWithParent extension : extensions) {
      if (removeFromParent(extension)) {
        removed.add(extension.extension());
        log.info(
            "Removed unexpected extension {} at location {}",
            extension.extension().getUrl(),
            extension.location());
      }
    }
    return removed;
  }
}
