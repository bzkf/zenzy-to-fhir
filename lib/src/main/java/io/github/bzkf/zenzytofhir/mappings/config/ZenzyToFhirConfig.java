package io.github.bzkf.zenzytofhir.mappings.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "zenzy-to-fhir")
public record ZenzyToFhirConfig(String version, Mappings mappings) {
  public record Mappings(Provenance provenance) {}

  public record Provenance(boolean enabled) {}
}
