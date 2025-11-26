package io.github.bzkf.zenzytofhir.lib;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * FHIR configuration properties bound from application-fhir.yaml
 */
@ConfigurationProperties(prefix = "fhir")
public record FhirProperties(String system) {
}
