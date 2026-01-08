<img align="right" width="250" height="47" src="media/Gematik_Logo_Flag.png"/> <br/>

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
    disease:
      concept-maps:
        - NotificationDiseaseCategoryToTransmissionCategory
    laboratory:
      concept-maps:
        - NotificationCategoryToTransmissionCategory
```

The `CodeMappingService` fetches all configured concept maps via the provided `CodeMappingClient`, merges them in declaration order (first value wins on duplicates) and caches the mappings. Reloads happen on startup and according to the configured cron expression. If no mappings can be loaded at all, the service raises a `CodeMappingUnavailableException` (error code `500`).

#### Retry Configuration

The Code Mapping Library uses an exponential backoff retry mechanism when loading concept maps. Retry parameters can be configured via Spring properties.

**Default Configuration:**

Without additional configuration, the following defaults are used:

```yaml
demis:
  codemapping:
    retry:
      initial-delay: 30s      # Initial delay before first retry
      max-delay: 15m          # Maximum delay between retries
      max-attempts: null      # Unlimited retry attempts
```

**Retry Behavior:**

The retry mechanism doubles the wait time with each attempt:
- 1st retry: 30 seconds
- 2nd retry: 60 seconds
- 3rd retry: 120 seconds (2 minutes)
- 4th retry: 240 seconds (4 minutes)
- 5th retry: 480 seconds (8 minutes)
- 6th retry: 900 seconds (15 minutes) ← max-delay reached
- 7+ retry: 900 seconds (15 minutes) ← stays at max-delay

**Configuration Examples:**

Development environment (faster retries):
```yaml
demis:
  codemapping:
    retry:
      initial-delay: 5s
      max-delay: 1m
      max-attempts: 10
```

Production-critical services (aggressive retries):
```yaml
demis:
  codemapping:
    retry:
      initial-delay: 10s
      max-delay: 5m
      max-attempts: 20
```

Limited retries with timeout:
```yaml
demis:
  codemapping:
    retry:
      initial-delay: 30s
      max-delay: 2m
      max-attempts: 5  # Stops after 5 attempts
```

**Properties Reference:**

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `demis.codemapping.retry.initial-delay` | Duration | `30s` | Initial wait time before the first retry |
| `demis.codemapping.retry.max-delay` | Duration | `15m` | Maximum wait time between retries |
| `demis.codemapping.retry.max-attempts` | Integer | `null` | Maximum number of retry attempts (null = unlimited) |

**Duration Format:**

Spring Boot supports various formats for Duration:
- Seconds: `30s`, `45s`
- Minutes: `5m`, `15m`
- Hours: `2h`
- Combined: `1h30m`, `2m30s`
- Milliseconds: `500ms`, `1000ms`


## Security Policy
If you want to see the security policy, please check our [SECURITY.md](.github/SECURITY.md).

## Contributing
If you want to contribute, please check our [CONTRIBUTING.md](.github/CONTRIBUTING.md).

## License
Copyright 2023-2025 gematik GmbH

EUROPEAN UNION PUBLIC LICENCE v. 1.2

EUPL © the European Union 2007, 2016

See the [LICENSE](./LICENSE.md) for the specific language governing permissions and limitations under the License

## Additional Notes and Disclaimer from gematik GmbH

1. Copyright notice: Each published work result is accompanied by an explicit statement of the license conditions for use. These are regularly typical conditions in connection with open source or free software. Programs described/provided/linked here are free software, unless otherwise stated.
2. Permission notice: Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
   1. The copyright notice (Item 1) and the permission notice (Item 2) shall be included in all copies or substantial portions of the Software.
   2. The software is provided "as is" without warranty of any kind, either express or implied, including, but not limited to, the warranties of fitness for a particular purpose, merchantability, and/or non-infringement. The authors or copyright holders shall not be liable in any manner whatsoever for any damages or other claims arising from, out of or in connection with the software or the use or other dealings with the software, whether in an action of contract, tort, or otherwise.
   3. We take open source license compliance very seriously. We are always striving to achieve compliance at all times and to improve our processes. If you find any issues or have any suggestions or comments, or if you see any other ways in which we can improve, please reach out to: ospo@gematik.de
3. Please note: Parts of this code may have been generated using AI-supported technology. Please take this into account, especially when troubleshooting, for security analyses and possible adjustments.

## Contact
E-Mail to [OSPO](mailto:ospo@gematik.de?subject=[OSPO]%20service-base)