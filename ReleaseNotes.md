<div style="text-align:right"><img src="https://raw.githubusercontent.com/gematik/gematik.github.io/master/Gematik_Logo_Flag_With_Background.png" width="250" height="47" alt="gematik GmbH Logo"/> <br/> </div> <br/>

# Release notes service-base

## Release 2.14.1
- renamed headers used for routing

## Release 2.14.0
- extended rest exception handling with UnsupportedOperationException processing
- services using the error handling can now return a HTTP 501 with a custom message for unsupported operations instead of a generic HTTP 500

## Release 2.13.1
- fixed a bug in the fallback logic for concept map retrieval when FHIR core split is enabled

## Release 2.13.0
- Extended CodeMappingService to support routing concept map requests to multiple FHIR profile instances via configurable `x-fhir-profile` headers (`demis.codemapping.fhir-profile-headers`)
- Added feature flag `feature.flag.fhir.core.split` (env: `FEATURE_FLAG_FHIR_CORE_SPLIT`, default: `false`) to enable the new header-based routing
- CodeMappingClient now provides two methods: `getConceptMap(String)` (legacy, without header) and `getConceptMapWithHeader(String, String)` (with `x-fhir-profile` header for routing)
- In default mode (feature flag disabled), the service first attempts the header-based call and automatically falls back to the legacy call without header on HTTP 403 — this ensures backward compatibility during the Istio routing transition
- When FHIR core split is enabled, an error for a concept map is only logged when all configured headers fail

## Release 2.12.0
- updated java to version 25

## Release 2.11.1
- Add property normalization for feign interceptor and update default header list

## Release 2.11.0
- Added header forwarding from inbound Spring controller request to outbound Feign client request

## Release 2.10.0
- Consolidated code mapping to use a single unified cache instead of separate disease and laboratory caches for better performance and consistency

## Release 2.9.0
- added code mapping client with service and caching for all services

## Release 2.8.2
- Updated Plugins and Libraries

## Release 2.8.1
- in fhir error operation outcome the error correlation id moved from location to diagnostics (FEATURE_FLAG_MOVE_ERROR_ID_TO_DIAGNOSTICS)

## Release 2.8.0
- Fhir support (fhir output in rest errorhandler, response converter, operationOutcome) - all opt-in

## Release 2.7.1
- Updated Spring Boot to 3.5.6

## Release 2.7.0
- Updated Spring Boot to 3.5.0

## Release 2.6.0
- Changed Tracing Format to W3C (OpenTelemetry)
- Updated ospo-resources for adding additional notes and disclaimer
- Updated dependencies

## Release 2.5.0
- Updated ospo-resources for adding additional notes and disclaimer

## Release 2.4.0
- Updated dependencies
- Updated OSPO-Guidelines and checks
- Add handling for Maven-Central release

## Release 2.2.3
- Updated dependencies

## Release 2.2.3
- Updated dependencies

## Release 2.1.1
- first official GitHub-Release
