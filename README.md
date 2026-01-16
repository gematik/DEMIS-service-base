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
    concept-maps:
      - NotificationDiseaseCategoryToTransmissionCategory
      - NotificationCategoryToTransmissionCategory
```

The `CodeMappingService` fetches all configured concept maps via the provided `CodeMappingClient`, merges them in declaration order (first value wins on duplicates) and caches the mappings. Reloads happen on startup and according to the configured cron expression. 

If a concept map cannot be loaded (e.g., due to network issues or missing maps), it will be logged and skipped, allowing the service to continue with the remaining concept maps. If no mappings can be loaded at all, the service raises a `CodeMappingUnavailableException` (error code `500`).


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