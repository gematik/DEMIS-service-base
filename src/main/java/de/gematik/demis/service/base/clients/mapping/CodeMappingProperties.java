package de.gematik.demis.service.base.clients.mapping;

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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "demis.codemapping")
public class CodeMappingProperties {

  private boolean enabled;
  private String cacheReloadCron;
  private final ClientProperties client = new ClientProperties();
  private final CategoryProperties disease = new CategoryProperties();
  private final CategoryProperties laboratory = new CategoryProperties();
  private final RetryProperties retry = new RetryProperties();

  @Getter
  @Setter
  public static class ClientProperties {
    private String baseUrl;
    private String contextPath;
  }

  @Getter
  public static class CategoryProperties {
    private final List<String> conceptMaps = new ArrayList<>();

    public void setConceptMaps(final List<String> conceptMaps) {
      this.conceptMaps.clear();
      if (conceptMaps != null) {
        this.conceptMaps.addAll(conceptMaps);
      }
    }
  }

  @Getter
  @Setter
  public static class RetryProperties {
    /** Initial delay before first retry. Default: 30 seconds */
    private Duration initialDelay = Duration.ofSeconds(30);

    /** Maximum delay between retries. Default: 15 minutes */
    private Duration maxDelay = Duration.ofMinutes(15);

    /** Maximum number of retry attempts. Default: null (unlimited) */
    private Integer maxAttempts = null;
  }
}
