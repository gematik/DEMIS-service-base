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

import de.gematik.demis.service.base.fhir.cleanup.LocationPath.PathSegment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class LocationPathTest {

  private static LocationPath parse(final String location) {
    return LocationPath.parse(location).orElseThrow();
  }

  @Test
  @DisplayName("splits a plain location into its segments without the leading Bundle")
  void shouldSplitPlainLocation() {
    final LocationPath path = parse("Bundle.entry[5].resource.birthDate.extension[0]");

    assertThat(path.segments())
        .containsExactly(
            new PathSegment("entry", 5),
            new PathSegment("resource", null),
            new PathSegment("birthDate", null),
            new PathSegment("extension", 0));
  }

  @Test
  @DisplayName("reads indexes with more than one digit")
  void shouldReadMultiDigitIndex() {
    final LocationPath path = parse("Bundle.entry[123].extension[10]");

    assertThat(path.segments())
        .containsExactly(new PathSegment("entry", 123), new PathSegment("extension", 10));
  }

  @Test
  @DisplayName("a segment without index has no index")
  void shouldMarkSegmentWithoutIndex() {
    final PathSegment withoutIndex = new PathSegment("resource", null);
    final PathSegment withIndex = new PathSegment("entry", 3);

    assertThat(withoutIndex.hasIndex()).isFalse();
    assertThat(withoutIndex.indexOrZero()).isZero();
    assertThat(withIndex.hasIndex()).isTrue();
    assertThat(withIndex.indexOrZero()).isEqualTo(3);
  }

  @Test
  @DisplayName("an index of zero is an index")
  void shouldTreatZeroAsIndex() {
    final PathSegment segment = parse("Bundle.extension[0]").last();

    assertThat(segment.hasIndex()).isTrue();
    assertThat(segment.indexOrZero()).isZero();
  }

  @Test
  @DisplayName("keeps the choice marker [x] as part of the name")
  void shouldKeepChoiceMarker() {
    final LocationPath path = parse("Bundle.entry[1].resource.value[x].extension[0]");

    assertThat(path.segments())
        .containsExactly(
            new PathSegment("entry", 1),
            new PathSegment("resource", null),
            new PathSegment("value[x]", null),
            new PathSegment("extension", 0));
  }

  @Test
  @DisplayName("last is the last segment, the parent path is everything before")
  void shouldProvideLastAndParentPath() {
    final LocationPath path = parse("Bundle.entry[1].resource.extension[2]");

    assertThat(path.last()).isEqualTo(new PathSegment("extension", 2));
    assertThat(path.parentPath())
        .containsExactly(new PathSegment("entry", 1), new PathSegment("resource", null));
  }

  @Test
  @DisplayName("the parent path is empty if the path has only one segment")
  void shouldHaveEmptyParentPathForSingleSegment() {
    final LocationPath path = parse("Bundle.extension[0]");

    assertThat(path.last()).isEqualTo(new PathSegment("extension", 0));
    assertThat(path.parentPath()).isEmpty();
  }

  @Test
  @DisplayName("inline comment should ignored")
  void shouldIgnoreInlineComment() {
    final LocationPath path =
        parse(
            "Bundle.entry[11].resource/*Observation/4A4306EA-F9CA-4081-964E-5BA029BAEA9E*/.value[x].extension[0]");

    assertThat(path.segments())
        .containsExactly(
            new PathSegment("entry", 11),
            new PathSegment("resource", null),
            new PathSegment("value[x]", null),
            new PathSegment("extension", 0));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "Parameters.parameter[0].resource.entry[1].extension[0]",
        "Parameters.parameter.resource.entry[1].extension[0]",
        "Parameters.parameter[0].resource/*Bundle/null*/.entry[1].extension[0]",
        "Parameters.parameter[0].resource/*Bundle/4A4306EA-F9CA-4081-964E-5BA029BAEA9E*/.entry[1].extension[0]",
      })
  @DisplayName("replaces the Parameters prefix by the bundle")
  void shouldReplaceParametersPrefix(final String location) {
    final LocationPath path = parse(location);

    assertThat(path.segments())
        .containsExactly(new PathSegment("entry", 1), new PathSegment("extension", 0));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(
      strings = {
        "   ",
        "this is not a path",
        "<<broken>>",
        "Bundle",
        "Bundle.",
        "Bundle..extension[0]",
        "Bundle.entry[a].extension[0]",
        "Bundle.entry[1].resource.ofType(Quantity).extension[0]",
        "Bundle.entry[1].resource.where(true).extension[0]",
        "Patient.extension[0]",
        "entry[1].extension[0]",
        "Parameters.parameter[1].resource.entry[1].extension[0]"
      })
  @DisplayName("rejects locations that are no valid path below a bundle")
  void shouldRejectInvalidLocation(final String location) {
    assertThat(LocationPath.parse(location)).isEmpty();
  }
}
