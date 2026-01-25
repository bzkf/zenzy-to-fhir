package io.github.bzkf.zenzytofhir.mappings;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "fhir")
public class FhirProperties {
  // TODO: need to figure out how to use a record for systems as well
  private FhirSystems systems;

  public FhirSystems getSystems() {
    return systems;
  }

  public void setSystems(FhirSystems systems) {
    this.systems = systems;
  }

  public record FhirSystems(FhirIdentifiers identifiers) {}

  public record FhirIdentifiers(String baseUrl, String zenzyTherapieNr, String patientId) {}
}
