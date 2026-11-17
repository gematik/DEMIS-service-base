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

import static de.gematik.demis.service.base.fhir.cleanup.ExtensionTestFixtures.ALLOWED;
import static de.gematik.demis.service.base.fhir.cleanup.ExtensionTestFixtures.UNEXPECTED;
import static de.gematik.demis.service.base.fhir.cleanup.ExtensionTestFixtures.UNEXPECTED_2;
import static de.gematik.demis.service.base.fhir.cleanup.ExtensionTestFixtures.bundleWithPatient;
import static de.gematik.demis.service.base.fhir.cleanup.ExtensionTestFixtures.extension;
import static de.gematik.demis.service.base.fhir.cleanup.ExtensionTestFixtures.extensionUrls;
import static de.gematik.demis.service.base.fhir.cleanup.ExtensionTestFixtures.patient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import de.gematik.demis.service.base.fhir.cleanup.ExtensionResolver.ExtensionWithParent;
import java.util.List;
import java.util.Set;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
class ExtensionRemoverTest {

  // the remover only uses the location of the resolved extension for logging
  private static final String LOCATION_1 = "irrelevant.just.needed.for.logging.1";
  private static final String LOCATION_2 = "irrelevant.just.needed.for.logging.2";

  private final Bundle bundle = bundleWithPatient(patient());

  @Mock ExtensionResolver extensionResolver;
  @InjectMocks ExtensionRemover underTest;

  private void mockResolver(final Set<String> locations, final ExtensionWithParent... resolved) {
    when(extensionResolver.resolve(bundle, locations)).thenReturn(List.of(resolved));
  }

  @Test
  void shouldLogEachRemovedExtensionWithUrlAndLocation() {
    final Patient parent = patient();
    final Extension first = extension(UNEXPECTED);
    final Extension second = extension(UNEXPECTED_2);
    parent.addExtension(first);
    parent.addExtension(second);
    final Set<String> locations = Set.of(LOCATION_1, LOCATION_2);
    mockResolver(
        locations,
        new ExtensionWithParent(parent, first, LOCATION_1),
        new ExtensionWithParent(parent, second, LOCATION_2));
    final Logger logger = (Logger) LoggerFactory.getLogger(ExtensionRemover.class);
    final ListAppender<ILoggingEvent> logAppender = new ListAppender<>();
    logAppender.start();
    logger.addAppender(logAppender);

    try {
      underTest.remove(bundle, locations);
    } finally {
      logger.detachAppender(logAppender);
      logAppender.stop();
    }

    assertThat(logAppender.list)
        .filteredOn(event -> event.getLevel() == Level.INFO)
        .extracting(ILoggingEvent::getFormattedMessage)
        .containsExactly(
            "Removed unexpected extension %s at location %s".formatted(UNEXPECTED, LOCATION_1),
            "Removed unexpected extension %s at location %s".formatted(UNEXPECTED_2, LOCATION_2));
  }

  @Test
  void shouldRemoveResolvedExtensionFromParentAndReturnIt() {
    final Patient parent = patient();
    final Extension unexpected = extension(UNEXPECTED);
    parent.addExtension(unexpected);
    final Set<String> locations = Set.of(LOCATION_1);
    mockResolver(locations, new ExtensionWithParent(parent, unexpected, LOCATION_1));

    final List<Extension> removed = underTest.remove(bundle, locations);

    assertThat(removed).containsExactly(unexpected);
    assertThat(parent.getExtension()).isEmpty();
  }

  @Test
  void shouldKeepOtherExtensionsOfParent() {
    final Patient parent = patient();
    final Extension unexpected = extension(UNEXPECTED);
    parent.addExtension(extension(ALLOWED));
    parent.addExtension(unexpected);
    final Set<String> locations = Set.of(LOCATION_1);
    mockResolver(locations, new ExtensionWithParent(parent, unexpected, LOCATION_1));

    underTest.remove(bundle, locations);

    assertThat(extensionUrls(parent.getExtension())).containsExactly(ALLOWED);
  }

  @Test
  void shouldRemoveByIdentityNotByUrl() {
    final Patient parent = patient();
    final Extension first = extension(UNEXPECTED);
    final Extension second = extension(UNEXPECTED);
    parent.addExtension(first);
    parent.addExtension(second);
    final Set<String> locations = Set.of(LOCATION_1);
    mockResolver(locations, new ExtensionWithParent(parent, second, LOCATION_1));

    final List<Extension> removed = underTest.remove(bundle, locations);

    assertThat(removed).containsExactly(second);
    assertThat(parent.getExtension()).hasSize(1);
    assertThat(parent.getExtension().getFirst()).isSameAs(first);
  }

  @Test
  void shouldRemoveSeveralExtensionsOfSameParent() {
    final Patient parent = patient();
    final Extension first = extension(UNEXPECTED);
    final Extension second = extension(UNEXPECTED_2);
    parent.addExtension(first);
    parent.addExtension(extension(ALLOWED));
    parent.addExtension(second);
    final Set<String> locations = Set.of(LOCATION_1, LOCATION_2);
    mockResolver(
        locations,
        new ExtensionWithParent(parent, first, LOCATION_1),
        new ExtensionWithParent(parent, second, LOCATION_2));

    final List<Extension> removed = underTest.remove(bundle, locations);

    assertThat(removed).containsExactly(first, second);
    assertThat(extensionUrls(parent.getExtension())).containsExactly(ALLOWED);
  }

  @Test
  void shouldRemoveExtensionsOfDifferentParents() {
    final Patient parent = patient();
    final Extension onPatient = extension(UNEXPECTED);
    final Extension onNested = extension(UNEXPECTED_2);
    parent.addExtension(onPatient);
    onPatient.addExtension(onNested);
    final Set<String> locations = Set.of(LOCATION_1, LOCATION_2);
    mockResolver(
        locations,
        new ExtensionWithParent(onPatient, onNested, LOCATION_1),
        new ExtensionWithParent(parent, onPatient, LOCATION_2));

    final List<Extension> removed = underTest.remove(bundle, locations);

    assertThat(removed).containsExactlyInAnyOrder(onNested, onPatient);
    assertThat(parent.getExtension()).isEmpty();
    assertThat(onPatient.getExtension()).isEmpty();
  }

  @Test
  void shouldRemoveAndReturnDuplicatedResolvedExtensionOnlyOnce() {
    final Patient parent = patient();
    final Extension unexpected = extension(UNEXPECTED);
    parent.addExtension(unexpected);
    parent.addExtension(extension(ALLOWED));
    final Set<String> locations = Set.of(LOCATION_1, LOCATION_2);
    mockResolver(
        locations,
        new ExtensionWithParent(parent, unexpected, LOCATION_1),
        new ExtensionWithParent(parent, unexpected, LOCATION_2));

    final List<Extension> removed = underTest.remove(bundle, locations);

    assertThat(removed).containsExactly(unexpected);
    assertThat(extensionUrls(parent.getExtension())).containsExactly(ALLOWED);
  }

  @Test
  void shouldNotReturnExtensionsThatWereAlreadyGone() {
    final Patient parent = patient();
    final Extension alreadyGone = extension(UNEXPECTED);
    final Set<String> locations = Set.of(LOCATION_1);
    mockResolver(locations, new ExtensionWithParent(parent, alreadyGone, LOCATION_1));

    assertThat(underTest.remove(bundle, locations)).isEmpty();
  }

  @Test
  void shouldRemoveResolvedExtensionsIfNotAllLocationsCouldBeResolved() {
    final Patient parent = patient();
    final Extension unexpected = extension(UNEXPECTED);
    parent.addExtension(unexpected);
    final Set<String> locations = Set.of(LOCATION_1, LOCATION_2);
    mockResolver(locations, new ExtensionWithParent(parent, unexpected, LOCATION_1));

    final List<Extension> removed = underTest.remove(bundle, locations);

    assertThat(removed).containsExactly(unexpected);
  }

  @Test
  void shouldDoNothingIfNothingCouldBeResolved() {
    final Patient parent = patient();
    parent.addExtension(extension(ALLOWED));
    final Set<String> locations = Set.of(LOCATION_1);
    mockResolver(locations);

    final List<Extension> removed = underTest.remove(bundle, locations);

    assertThat(removed).isEmpty();
    assertThat(parent.getExtension()).hasSize(1);
  }

  @Test
  void shouldNotTouchModifierExtensionsOfParent() {
    final Patient parent = patient();
    final Extension unexpected = extension(UNEXPECTED);
    parent.addExtension(unexpected);
    parent.addModifierExtension(extension(UNEXPECTED));
    final Set<String> locations = Set.of(LOCATION_1);
    mockResolver(locations, new ExtensionWithParent(parent, unexpected, LOCATION_1));

    underTest.remove(bundle, locations);

    assertThat(parent.getModifierExtension()).hasSize(1);
  }
}
