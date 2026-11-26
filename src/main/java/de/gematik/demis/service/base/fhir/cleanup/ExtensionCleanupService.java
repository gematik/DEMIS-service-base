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

import de.gematik.demis.service.base.error.ServiceException;
import de.gematik.demis.service.base.fhir.cleanup.OrphanedResourceRemover.RemovedEntry;
import jakarta.annotation.PostConstruct;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.OperationOutcomeIssueComponent;
import org.hl7.fhir.r4.model.StringType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
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

  /** Error code of the {@link ServiceException} thrown if the cleanup fails. */
  private static final String ERROR_CODE_CLEANUP_FAILED = "EXTENSION_CLEANUP_FAILED";

  private static final String ERROR_MESSAGE_CLEANUP_FAILED =
      "Meldung konnte nicht verarbeitet werden wegen Fehler bei Behandlung der Extensions.";

  private static final String OUTCOME_DIAGNOSTICS_REMOVED_EXTENSIONS =
      "%d unerwartete Extension(s) wurden entfernt";
  private static final String OUTCOME_DIAGNOSTICS_REMOVED_RESOURCE =
      "Resource '%s' wurde aus dem Bundle entfernt, da sie nicht mehr referenziert wird.";

  private final ExtensionRemover extensionRemover;
  private final OrphanedResourceRemover orphanedResourceRemover;

  @Getter(AccessLevel.PACKAGE)
  @Setter(AccessLevel.PACKAGE)
  @Value("${feature.flag.extension-cleanup-fail-on-error:false}")
  private boolean failOnError;

  @PostConstruct
  void logFF() {
    if (failOnError) {
      log.info(
          "'FEATURE_FLAG_ERROR_ON_EXTENSION_PROCESSING_ENABLED' is enabled: if the cleanup of unexpected extensions fails, a ServiceException (HTTP status 422) is thrown.");
    }
  }

  /**
   * Removes all extensions from the bundle that are reported as unexpected in the validation
   * outcome. If at least one removed extension contained a resource reference, the resources that
   * became orphaned by the removal are removed from the bundle as well.
   *
   * <p>The method is lenient by default: if a location cannot be resolved or an unexpected error
   * occurs, it is logged and processing continues with the (possibly partially cleaned) bundle.
   * Only if the feature flag {@code feature.flag.extension-cleanup-fail-on-error} is enabled, such
   * problems are escalated as {@link ServiceException}. This is the current behavior only: in the
   * future, a {@link ServiceException} will always be thrown and the feature flag will be removed.
   *
   * <p>Nothing happens if {@code bundle} or {@code validationOutcome} is {@code null} or the
   * outcome reports no unexpected extensions.
   *
   * <p><strong>Must be called before any other modification of the bundle, otherwise the element
   * indexes contained in the validation locations no longer match the bundle.</strong>
   *
   * @param bundle the bundle to clean, is modified in place
   * @param validationOutcome the outcome of the fhir validation, receives an additional warning
   *     issue for the removed extensions and for each removed resource
   * @throws ServiceException with status {@code 422 (UNPROCESSABLE_CONTENT)} and error code {@code
   *     EXTENSION_CLEANUP_FAILED}, only if {@code feature.flag.extension-cleanup-fail-on-error} is
   *     {@code true} and an unexpected extension has no location, could not be resolved / removed,
   *     or the cleanup failed with a runtime error
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
      if (removedExtensions.size() < locations.size()) {
        log.error(
            "{} of {} unexpected extension(s) reported by the validation service could not be resolved / removed",
            locations.size() - removedExtensions.size(),
            locations.size());
        failIfConfigured();
      }
      if (removedExtensions.isEmpty()) {
        return;
      }
      addOperationOutcomeWarningForRemovedExtensions(validationOutcome, removedExtensions);

      final List<RemovedEntry> removedResourceEntries =
          orphanedResourceRemover.removeReferencedResourcesIfOrphaned(bundle, removedExtensions);
      addOperationOutcomeWarningsForRemovedResources(
          validationOutcome, removedResourceEntries, locations);
    } catch (final ServiceException e) {
      throw e;
    } catch (final RuntimeException e) {
      log.error(
          "error removing unexpected extensions (including orphaned resources). {}",
          failOnError ? "Abort processing" : "Ignore and proceed processing",
          e);
      failIfConfigured();
    }
  }

  private void failIfConfigured() {
    if (failOnError) {
      throw new ServiceException(
          HttpStatus.UNPROCESSABLE_CONTENT,
          ERROR_CODE_CLEANUP_FAILED,
          ERROR_MESSAGE_CLEANUP_FAILED);
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
        .map(this::firstLocation)
        .flatMap(Optional::stream)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  /** The first non-empty location is the path of the extension, further entries are ignored. */
  private Optional<String> firstLocation(final OperationOutcomeIssueComponent issue) {
    final Optional<String> location =
        issue.getLocation().stream()
            .map(StringType::getValue)
            .filter(value -> value != null && !value.isBlank())
            .findFirst()
            .map(String::trim);
    if (location.isEmpty()) {
      log.error(
          "Unexpected extension reported by the validation service without a location: severity={}, code={}, details={}, diagnostics={}",
          issue.getSeverity(),
          issue.getCode(),
          issue.getDetails().getCodingFirstRep().getCode(),
          issue.getDiagnostics());
      failIfConfigured();
    }
    return location;
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
