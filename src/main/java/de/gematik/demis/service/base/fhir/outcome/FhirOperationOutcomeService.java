package de.gematik.demis.service.base.fhir.outcome;

/*-
 * #%L
 * service-base
 * %%
 * Copyright (C) 2025 gematik GmbH
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
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 * #L%
 */

import java.util.Comparator;
import lombok.RequiredArgsConstructor;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Narrative;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.utilities.xhtml.NodeType;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FhirOperationOutcomeService {

  private final FhirOperationOutcomeProperties props;

  private final SeverityComparator severityComparator = new SeverityComparator();

  public void processOutcome(final OperationOutcome outcome) {
    applyProfile(outcome);
    outcome.setText(createOutcomeText());
    filterOutcomeIssues(outcome);
    orderOutcomeIssues(outcome);
  }

  private void applyProfile(final OperationOutcome outcome) {
    final String profile = props.profile();
    if (profile == null || profile.isBlank()) {
      return;
    }

    final Meta meta = outcome.getMeta();
    final boolean hasProfile =
        meta.getProfile().stream().map(CanonicalType::asStringValue).anyMatch(profile::equals);

    if (!hasProfile) {
      meta.addProfile(profile);
    }
  }

  private void filterOutcomeIssues(final OperationOutcome outcome) {
    if (!props.issueFilter() || props.issueThreshold() == null) {
      // no filtering
      return;
    }
    outcome
        .getIssue()
        .removeIf(
            issue -> severityComparator.compare(issue.getSeverity(), props.issueThreshold()) < 0);
  }

  private void orderOutcomeIssues(final OperationOutcome outcome) {
    if (props.sort()) {
      outcome
          .getIssue()
          .sort(
              Comparator.comparing(
                  OperationOutcome.OperationOutcomeIssueComponent::getSeverity,
                  severityComparator.reversed()));
    }
  }

  public OperationOutcome.OperationOutcomeIssueComponent allOk() {
    return new OperationOutcome.OperationOutcomeIssueComponent()
        .setSeverity(OperationOutcome.IssueSeverity.INFORMATION)
        .setCode(OperationOutcome.IssueType.INFORMATIONAL)
        .setDetails(new CodeableConcept().setText("All OK"));
  }

  private Narrative createOutcomeText() {
    var div = new XhtmlNode(NodeType.Element, "div");
    div.addText("");
    return new Narrative().setStatus(Narrative.NarrativeStatus.GENERATED).setDiv(div);
  }
}
