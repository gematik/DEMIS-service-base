<div style="text-align:right"><img src="https://raw.githubusercontent.com/gematik/gematik.github.io/master/Gematik_Logo_Flag_With_Background.png" width="250" height="47" alt="gematik GmbH Logo"/> <br/> </div> <br/>

# Release notes service-base

## 2.13.0
- Extended CodeMappingService to support routing concept map requests to multiple FHIR profile instances via configurable `x-fhir-profile` headers (`demis.codemapping.fhir-profile-headers`)
- Added feature flag `feature.flag.fhir.core.split` (env: `FEATURE_FLAG_FHIR_CORE_SPLIT`, default: `false`) to enable the new header-based routing. When disabled, the default header `fhir-profile-snapshots` is used
- Consolidated CodeMappingClient to a single `getConceptMap(String, String)` method — the `x-fhir-profile` header is now always passed explicitly by the caller
- Adjusted error handling: when FHIR core split is enabled, an error for a concept map is only logged when all configured headers fail
- Added integration tests for both legacy mode (feature flag disabled) and FHIR core split mode (feature flag enabled)

## 2.12.0
- updated java to version 25

## 2.11.1
- Add property normalization for feign interceptor and update default header list

## 2.11.0
- Added header forwarding from inbound Spring controller request to outbound Feign client request

## 2.10.0
- Consolidated code mapping to use a single unified cache instead of separate disease and laboratory caches for better performance and consistency

## 2.9.0
- added code mapping client with service and caching for all services

## 2.8.2
- Updated Plugins and Libraries

## 2.8.1
- in fhir error operation outcome the error correlation id moved from location to diagnostics (FEATURE_FLAG_MOVE_ERROR_ID_TO_DIAGNOSTICS)

## 2.8.0
- Fhir support (fhir output in rest errorhandler, response converter, operationOutcome) - all opt-in

## 2.7.1
- Updated Spring Boot to 3.5.6

## 2.7.0
- Updated Spring Boot to 3.5.0

## 2.6.0
- Changed Tracing Format to W3C (OpenTelemetry)
- Updated ospo-resources for adding additional notes and disclaimer
- Updated dependencies

## 2.5.0
- Updated ospo-resources for adding additional notes and disclaimer

## 2.4.0
- Updated dependencies
- Updated OSPO-Guidelines and checks
- Add handling for Maven-Central release

## 2.2.3
- Updated dependencies

## 2.2.3
- Updated dependencies

## 2.1.1
- first official GitHub-Release
