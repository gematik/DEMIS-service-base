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

import de.gematik.demis.service.base.fhir.cleanup.LocationPath.PathSegment;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.instance.model.api.IBaseExtension;
import org.hl7.fhir.instance.model.api.IBaseHasExtensions;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Property;
import org.springframework.stereotype.Component;

/**
 * Resolves validation locations to the extensions of a bundle. The bundle is not modified.
 *
 * <p>The bundle is walked segment by segment via {@link Base#getChildByName(String)}.
 *
 * <p>This class never throws. Locations that cannot be resolved are logged and skipped.
 */
@Slf4j
@Component
class ExtensionResolver {

  private static final String EXTENSION = "extension";

  /**
   * Walks from the start element along the given path.
   *
   * @return the addressed element or {@link Optional#empty()} if the path leads nowhere
   */
  private static Optional<Base> walk(final Base start, final List<PathSegment> path) {
    Base current = start;
    for (final PathSegment segment : path) {
      final Property child = current.getChildByName(segment.name());
      if (child == null) {
        return Optional.empty();
      }
      final List<Base> values = child.getValues();
      if (segment.indexOrZero() >= values.size()) {
        return Optional.empty();
      }
      current = values.get(segment.indexOrZero());
    }
    return Optional.of(current);
  }

  /**
   * @param bundle the bundle the locations refer to
   * @param locations the validation issue locations pointing to extensions
   * @return the resolved extensions with their parents, locations that cannot be resolved are
   *     missing
   */
  List<ExtensionWithParent> resolve(final Bundle bundle, final Set<String> locations) {
    return locations.stream()
        .map(location -> resolve(bundle, location))
        .flatMap(Optional::stream)
        .toList();
  }

  private Optional<ExtensionWithParent> resolve(final Bundle bundle, final String location) {
    try {
      final Optional<LocationPath> parsed = LocationPath.parse(location);
      if (parsed.isEmpty()) {
        log.error("location '{}' is not a valid path, ignoring it", location);
        return Optional.empty();
      }
      final LocationPath path = parsed.get();

      final PathSegment last = path.last();
      if (!EXTENSION.equals(last.name()) || !last.hasIndex()) {
        log.error("location '{}' does not point to an extension, ignoring it", location);
        return Optional.empty();
      }

      final Optional<Base> parent = walk(bundle, path.parentPath());
      if (parent.isEmpty()) {
        log.error("Could not resolve the parent element of location '{}'", location);
        return Optional.empty();
      }
      if (!(parent.get() instanceof IBaseHasExtensions extensionParent)) {
        log.error("Parent element of location '{}' cannot hold extensions, ignoring it", location);
        return Optional.empty();
      }

      final List<? extends IBaseExtension<?, ?>> extensions = extensionParent.getExtension();
      if (last.index() >= extensions.size()) {
        log.error(
            "element has only {} extension(s), but location '{}' requires index {}",
            extensions.size(),
            location,
            last.index());
        return Optional.empty();
      }
      if (!(extensions.get(last.index()) instanceof Extension extension)) {
        log.error("expect Extension but found type {}", extensions.get(last.index()).getClass());
        return Optional.empty();
      }

      return Optional.of(new ExtensionWithParent(extensionParent, extension, location));
    } catch (final Exception ex) {
      log.error("Could not process validation location '{}'", location, ex);
      return Optional.empty();
    }
  }

  /**
   * An extension that was resolved from a validation location, together with the element holding
   * it.
   *
   * @param parent the element holding the extension
   * @param extension the extension itself
   * @param location the validation location the extension was resolved from
   */
  record ExtensionWithParent(IBaseHasExtensions parent, Extension extension, String location) {}
}
