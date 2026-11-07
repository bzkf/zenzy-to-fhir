package io.github.bzkf.zenzytofhir.mappings;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Configuration;

// explicitly named to not clash with the to-fhir starter's "fhirProperties" bean
@Configuration("zenzyFhirProperties")
@ConfigurationProperties(prefix = "fhir")
@ConfigurationPropertiesScan
public class FhirProperties {
  private FhirSystems systems;
  private FhirProfiles profiles;
  private io.github.dizuker.tofhir.config.FhirProperties fhir;

  public FhirProperties(io.github.dizuker.tofhir.config.FhirProperties fhir) {
    this.fhir = fhir;
  }

  public io.github.dizuker.tofhir.config.FhirProperties fhir() {
    return fhir;
  }

  public FhirSystems getSystems() {
    return systems;
  }

  public FhirProfiles getProfiles() {
    return profiles;
  }

  public void setProfiles(FhirProfiles profiles) {
    this.profiles = profiles;
  }

  public void setSystems(FhirSystems systems) {
    this.systems = systems;
  }

  public record FhirSystems(FhirIdentifiers identifiers) {}

  public record FhirIdentifiers(
      String patientId,
      String therapieMedicationRequestId,
      String therapieMedicationId,
      String therapieWirkstoffMedicationId,
      String therapieTraegerloesungMedicationId,
      String deviceId) {}

  public record FhirProfiles(String miiMedication, String miiMedicationRequest) {}
}
