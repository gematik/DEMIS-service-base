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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A parsed {@code OperationOutcome.issue.location}, relative to a bundle.
 *
 * <p>A location like {@code Bundle.entry[5].resource.birthDate.extension[0]} is split into the
 * segments {@code entry[5]}, {@code resource}, {@code birthDate} and {@code extension[0]}. The
 * leading {@code Bundle} is not part of the path.
 *
 * <p>Besides plain paths the following notations of the validator are understood:
 *
 * <ul>
 *   <li>the {@code Parameters.parameter[0].resource} prefix, which is replaced by {@code Bundle}
 *   <li>inline comments like {@code resource/*Observation/<id>*}{@code /}
 *   <li>choice elements like {@code value[x]}
 * </ul>
 *
 * @param segments the segments below the bundle, never empty
 */
record LocationPath(List<PathSegment> segments) {

  private static final String BUNDLE = "Bundle";
  private static final String PARAMETERS_BUNDLE_PATH = "Parameters.parameter[0].resource";

  /** Matches inline comments like slash-star ... star-slash. */
  private static final Pattern COMMENT = Pattern.compile("/\\*.*?\\*/");

  /** Matches the Parameters prefix; comments must already be removed. */
  private static final Pattern PARAMETERS_PREFIX =
      Pattern.compile("^Parameters\\.parameter(\\[0])?\\.resource(?=\\.|$)");

  /** Matches {@code name}, {@code name[3]} and {@code name[x]}. Group 2 is the optional index. */
  private static final Pattern SEGMENT = Pattern.compile("([A-Za-z]\\w*(?:\\[x])?)(?:\\[(\\d+)])?");

  /**
   * @param location the raw location delivered by the validator
   * @return the path of the bundle the location belongs to: {@code
   *     Parameters.parameter[0].resource} if the bundle is wrapped in a Parameters resource,
   *     otherwise {@code Bundle}
   */
  static String bundlePrefix(final String location) {
    if (location == null) {
      return BUNDLE;
    }
    final String path = COMMENT.matcher(location.trim()).replaceAll("");
    return PARAMETERS_PREFIX.matcher(path).find() ? PARAMETERS_BUNDLE_PATH : BUNDLE;
  }

  /**
   * @param location the raw location delivered by the validator
   * @return the parsed location or {@link Optional#empty()} if it is not a valid path below a
   *     bundle
   */
  static Optional<LocationPath> parse(final String location) {
    if (location == null || location.isBlank()) {
      return Optional.empty();
    }

    String path = COMMENT.matcher(location.trim()).replaceAll("");
    path = PARAMETERS_PREFIX.matcher(path).replaceFirst(BUNDLE);

    final String[] parts = path.split("\\.");
    if (parts.length < 2 || !BUNDLE.equals(parts[0])) {
      return Optional.empty();
    }

    final List<PathSegment> segments = new ArrayList<>(parts.length - 1);
    for (int i = 1; i < parts.length; i++) {
      final Matcher matcher = SEGMENT.matcher(parts[i]);
      if (!matcher.matches()) {
        return Optional.empty();
      }
      final Integer index = matcher.group(2) == null ? null : Integer.valueOf(matcher.group(2));
      segments.add(new PathSegment(matcher.group(1), index));
    }
    return Optional.of(new LocationPath(List.copyOf(segments)));
  }

  /**
   * @return the last segment, e.g. {@code extension[0]}
   */
  PathSegment last() {
    return segments.getLast();
  }

  /**
   * @return all segments except the last one, empty if the path has only one segment
   */
  List<PathSegment> parentPath() {
    return segments.subList(0, segments.size() - 1);
  }

  /**
   * One step of the path.
   *
   * @param name the name of the child element, e.g. {@code entry} or {@code value[x]}
   * @param index the index within the child elements, {@code null} if the segment has no index
   */
  record PathSegment(String name, Integer index) {

    boolean hasIndex() {
      return index != null;
    }

    /**
     * @return the index, {@code 0} if the segment has none (single valued element)
     */
    int indexOrZero() {
      return index == null ? 0 : index;
    }
  }
}
