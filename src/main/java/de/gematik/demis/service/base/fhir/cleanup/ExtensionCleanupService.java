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

import de.gematik.demis.service.base.fhir.cleanup.OrphanedResourceRemover.RemovedEntry;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.StringType;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Service;

/**
 * Removes extensions from a bundle that the validation service reported as unexpected and afterward
 * removes the resources that became orphaned by this.
 *
 * <p>The detection logic itself lives in the validation service, this class only evaluates the
 * reported {@code OperationOutcome.issue.location} values. The existing issues stay untouched; for
 * every resource removed from the bundle an additional warning issue is added to the outcome.
 *
 * <p>The processing has the following steps:
 *
 * <ol>
 *   <li>the outcome is reduced to the locations of the unexpected extensions
 *   <li>the {@link ExtensionRemover} resolves the locations and removes the extensions from the
 *       bundle
 *   <li>the {@link OrphanedResourceRemover} removes resources that were referenced only by the
 *       removed extensions and are now orphaned
 * </ol>
 */
@Slf4j
@Service
@Import({ExtensionRemover.class, ExtensionResolver.class, OrphanedResourceRemover.class})
@RequiredArgsConstructor
public class ExtensionCleanupService {

  /** Issue code the validation service uses for unexpected extensions. */
  private static final String UNEXPECTED_EXTENSION_CODE = "Unexpected_Extension";

  private static final String OUTCOME_DIAGNOSTICS_REMOVED_EXTENSIONS =
      "%d unerwartete Extension(s) wurden entfernt";
  private static final String OUTCOME_DIAGNOSTICS_REMOVED_RESOURCE =
      "Resource '%s' wurde aus dem Bundle entfernt, da sie nicht mehr referenziert wird.";

  private final ExtensionRemover extensionRemover;
  private final OrphanedResourceRemover orphanedResourceRemover;

  /**
   * Removes all extensions from the bundle that are reported as unexpected in the validation
   * outcome. If at least one removed extension contained a resource reference, the resources that
   * became orphaned by the removal are removed from the bundle as well.
   *
   * <p><strong>Must be called before any other modification of the bundle, otherwise the element
   * indexes contained in the validation locations no longer match the bundle.</strong>
   *
   * @param bundle the bundle to clean, is modified in place
   * @param validationOutcome the outcome of the fhir validation, receives a warning for each
   *     removed resource
   */
  public void cleanupUnexpectedExtensions(
      final Bundle bundle, final OperationOutcome validationOutcome) {
    if (bundle == null || validationOutcome == null) {
      return;
    }

    try {
      final Set<String> locations = collectUnexpectedExtensionLocations(validationOutcome);
      if (locations.isEmpty()) {
        return;
      }

      final List<Extension> removedExtensions = extensionRemover.remove(bundle, locations);
      if (removedExtensions.isEmpty()) {
        return;
      }
      addOperationOutcomeWarningForRemovedExtensions(validationOutcome, removedExtensions);

      final List<RemovedEntry> removedResourceEntries =
          orphanedResourceRemover.removeReferencedResourcesIfOrphaned(bundle, removedExtensions);
      addOperationOutcomeWarningsForRemovedResources(
          validationOutcome, removedResourceEntries, locations);
    } catch (final RuntimeException e) {
      log.error(
          "error removing unexpected extensions (including orphaned resources). Ignore and proceed processing",
          e);
    }
  }

  private Set<String> collectUnexpectedExtensionLocations(final OperationOutcome outcome) {
    return outcome.getIssue().stream()
        .filter(
            issue ->
                issue.hasDetails()
                    && issue.getDetails().getCoding().stream()
                        .map(Coding::getCode)
                        .anyMatch(UNEXPECTED_EXTENSION_CODE::equals))
        .flatMap(issue -> issue.getLocation().stream())
        .map(StringType::getValue)
        .filter(location -> location != null && !location.isBlank())
        .map(String::trim)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  private void addOperationOutcomeWarningForRemovedExtensions(
      final OperationOutcome outcome, final List<Extension> removedExtensions) {
    outcome
        .addIssue()
        .setSeverity(OperationOutcome.IssueSeverity.WARNING)
        .setCode(OperationOutcome.IssueType.INFORMATIONAL)
        .setDiagnostics(OUTCOME_DIAGNOSTICS_REMOVED_EXTENSIONS.formatted(removedExtensions.size()));
  }

  private void addOperationOutcomeWarningsForRemovedResources(
      final OperationOutcome validationOutcome,
      final List<RemovedEntry> removedResourceEntries,
      final Set<String> locations) {
    if (removedResourceEntries.isEmpty()) {
      return;
    }
    final String bundlePrefix = LocationPath.bundlePrefix(locations.iterator().next());
    removedResourceEntries.forEach(
        entry ->
            addOperationOutcomeWarningIssueForRemovedResource(
                validationOutcome, bundlePrefix, entry));
  }

  private void addOperationOutcomeWarningIssueForRemovedResource(
      final OperationOutcome outcome, final String bundlePrefix, final RemovedEntry entry) {
    final String location = "%s.entry[%d]".formatted(bundlePrefix, entry.index());
    final String resource = entry.fullUrl() == null ? location : entry.fullUrl();
    outcome
        .addIssue()
        .setSeverity(OperationOutcome.IssueSeverity.WARNING)
        .setCode(OperationOutcome.IssueType.INFORMATIONAL)
        .setDiagnostics(OUTCOME_DIAGNOSTICS_REMOVED_RESOURCE.formatted(resource))
        .addLocation(location);
  }
}
