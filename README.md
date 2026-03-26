<div style="text-align:right"><img src="https://raw.githubusercontent.com/gematik/gematik.github.io/master/Gematik_Logo_Flag_With_Background.png" width="250" height="47" alt="gematik GmbH Logo"/> <br/> </div> <br/>

# Service Base

<details>
  <summary>Table of Contents</summary>
  <ol>
    <li><a href="#about-the-project">About The Project</a></li>
    <li><a href="#getting-started">Getting Started</a></li>
    <li><a href="#security-policy">Security Policy</a></li>
    <li><a href="#contributing">Contributing</a></li>
    <li><a href="#license">License</a></li>
    <li><a href="#contact">Contact</a></li>
  </ol>
</details>

## About The Project

This project provides basic tools for metrics, logging and error handling, among other things.

### Quality Gate

[![Quality Gate Status](https://sonar.prod.ccs.gematik.solutions/api/project_badges/measure?project=de.gematik.demis%3Aservice-base&metric=alert_status&token=sqb_204b4d67922949e6f77c821ba32d05b83a471917)](https://sonar.prod.ccs.gematik.solutions/dashboard?id=de.gematik.demis%3Aservice-base)
[![Vulnerabilities](https://sonar.prod.ccs.gematik.solutions/api/project_badges/measure?project=de.gematik.demis%3Aservice-base&metric=vulnerabilities&token=sqb_204b4d67922949e6f77c821ba32d05b83a471917)](https://sonar.prod.ccs.gematik.solutions/dashboard?id=de.gematik.demis%3Aservice-base)
[![Bugs](https://sonar.prod.ccs.gematik.solutions/api/project_badges/measure?project=de.gematik.demis%3Aservice-base&metric=bugs&token=sqb_204b4d67922949e6f77c821ba32d05b83a471917)](https://sonar.prod.ccs.gematik.solutions/dashboard?id=de.gematik.demis%3Aservice-base)
[![Code Smells](https://sonar.prod.ccs.gematik.solutions/api/project_badges/measure?project=de.gematik.demis%3Aservice-base&metric=code_smells&token=sqb_204b4d67922949e6f77c821ba32d05b83a471917)](https://sonar.prod.ccs.gematik.solutions/dashboard?id=de.gematik.demis%3Aservice-base)
[![Lines of Code](https://sonar.prod.ccs.gematik.solutions/api/project_badges/measure?project=de.gematik.demis%3Aservice-base&metric=ncloc&token=sqb_204b4d67922949e6f77c821ba32d05b83a471917)](https://sonar.prod.ccs.gematik.solutions/dashboard?id=de.gematik.demis%3Aservice-base)
[![Coverage](https://sonar.prod.ccs.gematik.solutions/api/project_badges/measure?project=de.gematik.demis%3Aservice-base&metric=coverage&token=sqb_204b4d67922949e6f77c821ba32d05b83a471917)](https://sonar.prod.ccs.gematik.solutions/dashboard?id=de.gematik.demis%3Aservice-base)


### Release Notes

See [ReleaseNotes](ReleaseNotes.md) for all information regarding the (newest) releases.


## Getting Started

If you want to use the service-base library you have to add the following dependency

            <dependency>
                <groupId>de.gematik.demis</groupId>
                <artifactId>service-base</artifactId>
            </dependency>

### Code Mapping Client

Enable the code-mapping auto configuration if your service requires centrally managed concept maps:

```yaml
demis:
  codemapping:
    enabled: true
    cache-reload-cron: 0 */5 * * * *
    client:
      base-url: https://futs.example.tld
      context-path: /
    concept-maps:
      - NotificationDiseaseCategoryToTransmissionCategory
      - NotificationCategoryToTransmissionCategory
```

The `CodeMappingService` fetches all configured concept maps via the provided `CodeMappingClient`, merges them in declaration order (first value wins on duplicates) and caches the mappings. Reloads happen on startup and according to the configured cron expression. 

If a concept map cannot be loaded (e.g., due to network issues or missing maps), it will be logged and skipped, allowing the service to continue with the remaining concept maps. If no mappings can be loaded at all, the service raises a `CodeMappingUnavailableException` (error code `500`).

#### FHIR Core Split

When the FHIR snapshots are split across multiple service instances (routed via the `x-fhir-package` header), enable the feature flag and configure the profile headers:

```yaml
feature:
  flag:
    fhir:
      core:
        split: true

demis:
  codemapping:
    enabled: true
    cache-reload-cron: 0 */5 * * * *
    client:
      base-url: https://futs.example.tld
      context-path: /
    concept-maps:
      - NotificationDiseaseCategoryToTransmissionCategory
      - NotificationCategoryToTransmissionCategory
    fhir-package-headers:
      - fhir-package-a
      - fhir-package-b
```

When `feature.flag.fhir.core.split=true`, the `CodeMappingService` iterates through all configured `fhir-package-headers` for each concept map, passing them as the `x-fhir-package` request header. Results from all successful calls are merged. An error for a concept map is only logged when **all** configured headers fail for that concept map.

## How DEMIS code mapping works

The DEMIS code mapping feature provides a way to resolve input codes (for example, disease codes) to target codes using an external Code Mapping Service. It is implemented in a way that minimizes network calls and shields callers from temporary outages by using an in-memory cache with periodic reloads.

### Components

- `CodeMappingClient`
  - A Spring Cloud OpenFeign client that calls the external Code Mapping Service.
  - Exposes a single operation `getConceptMap(String name, String fhirPackage)` that returns a key/value map for a given concept map name, routing via the `x-fhir-package` header.

- `CodeMappingService`
  - Main entry point for application code.
  - Uses `CodeMappingClient` to fetch concept maps and keeps them in a read-only in-memory cache (`ReloadableCache`).
  - Public API: `mapCode(String diseaseCode)` which returns the mapped value (or `null` if no mapping exists).

- `ReloadableCache`
  - Small, generic cache wrapper that holds the merged concept maps.
  - Uses a `Supplier<Map<String, String>>` to (re)load data and swaps the internal map atomically.

- `CodeMappingProperties`
  - Binds to the `demis.codemapping.*` configuration properties.
  - Controls whether the feature is enabled, which concept maps to load, how often the cache is reloaded, and the list of FHIR profile headers.

- `CodeMappingAutoConfiguration`
  - Auto-configures `CodeMappingService` and its dependencies when `demis.codemapping.enabled=true` and Spring Cloud OpenFeign is on the classpath.

### High-level flow

1. **Startup / bean creation**
   - When `demis.codemapping.enabled=true`, Spring Boot creates `CodeMappingService` via `CodeMappingAutoConfiguration`.
   - The constructor of `CodeMappingService` validates `CodeMappingProperties` and prepares the list of concept maps to load.
   - When the FHIR core split feature flag is enabled, at least one FHIR profile header must also be configured.
   - A `ReloadableCache<String, String>` is created with a supplier pointing to `CodeMappingService.loadConceptMaps(List<String>)`.

2. **Initial load (lazy)**
   - The cache is not preloaded during construction.
   - On the first call to `CodeMappingService.mapCode(String)`, the service checks whether the cache already has entries.
   - If not, it calls `cache.loadCache()`, which in turn uses the supplier to fetch all configured concept maps via the `CodeMappingClient`.
   - All returned concept maps are merged into a single `Map<String, String>`; duplicate keys keep the first value and are logged as warnings.
   - Only a non-empty result replaces the current cache snapshot.

3. **Scheduled reload**
   - A scheduled method `loadConceptMapsScheduled()` is annotated with `@Scheduled(cron = "${demis.codemapping.cache-reload-cron}")`.
   - According to the configured cron expression, this method periodically triggers `cache.loadCache()`.
   - The reload replaces the internal immutable map atomically, so concurrent readers either see the old snapshot or the new one, but never a partially updated map.

4. **Using the mapping API**
   - Callers resolve a code via `CodeMappingService.mapCode(String diseaseCode)`.
   - If no cache data is available even after a reload attempt, a `CodeMappingUnavailableException` is thrown to signal that mappings are currently not available.
   - If the cache contains data but the specific key is missing, the method logs this and returns `null` so callers can decide how to handle unmapped codes.

### Configuration

The following properties control DEMIS code mapping (see `CodeMappingProperties`):

- `demis.codemapping.enabled` (boolean)
  - Enables or disables the code mapping feature.

- `demis.codemapping.cache-reload-cron` (String)
  - Cron expression for periodic cache reloads.

- `demis.codemapping.client.base-url` (String)
  - Base URL of the external Code Mapping Service used by the Feign client.

- `demis.codemapping.client.context-path` (String)
  - Context path that is prefixed to the concept map endpoint.

- `demis.codemapping.concept-maps` (List<String>)
  - Names of the concept maps to load and merge into the cache.

- `demis.codemapping.fhir-package-headers` (List<String>)
  - List of `x-fhir-package` header values used when `feature.flag.fhir.core.split=true`. Each header is used to fetch concept maps from the corresponding FHIR profile instance.

- `feature.flag.fhir.core.split` (boolean)
  - Enables or disables the FHIR core split feature. When enabled, the service uses the configured `fhir-package-headers` to route requests to specific FHIR profile instances.

## Security Policy
If you want to see the security policy, please check our [SECURITY.md](.github/SECURITY.md).

## Contributing
If you want to contribute, please check our [CONTRIBUTING.md](.github/CONTRIBUTING.md).

## License
Copyright 2023-2026 gematik GmbH

EUROPEAN UNION PUBLIC LICENCE v. 1.2

EUPL © the European Union 2007, 2016

See the [LICENSE](./LICENSE.md) for the specific language governing permissions and limitations under the License

## Additional Notes and Disclaimer from gematik GmbH

1. Copyright notice: Each published work result is accompanied by an explicit statement of the license conditions for use. These are regularly typical conditions in connection with open source or free software. Programs described/provided/linked here are free software, unless otherwise stated.
2. Permission notice: Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
    1. The copyright notice (Item 1) and the permission notice (Item 2) shall be included in all copies or substantial portions of the Software.
    2. The software is provided "as is" without warranty of any kind, either express or implied, including, but not limited to, the warranties of fitness for a particular purpose, merchantability, and/or non-infringement. The authors or copyright holders shall not be liable in any manner whatsoever for any damages or other claims arising from, out of or in connection with the software or the use or other dealings with the software, whether in an action of contract, tort, or otherwise.
    3. We take open source license compliance very seriously. We are always striving to achieve compliance at all times and to improve our processes. If you find any issues or have any suggestions or comments, or if you see any other ways in which we can improve, please reach out to: ospo@gematik.de
3. Parts of this software and - in isolated cases - content such as text or images may have been developed using the support of AI tools. They are subject to the same reviews, tests, and security checks as any other contribution. The functionality of the software itself is not based on AI decisions.

## Contact
E-Mail to [OSPO](mailto:ospo@gematik.de?subject=[OSPO]%20service-base)
