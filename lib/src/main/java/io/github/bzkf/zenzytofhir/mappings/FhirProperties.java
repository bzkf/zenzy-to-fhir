package io.github.bzkf.zenzytofhir.mappings;

import io.github.dizuker.tofhir.config.ToFhirProperties;
import io.github.dizuker.tofhir.config.ToFhirProperties.Fhir;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "fhir")
@ConfigurationPropertiesScan
public class FhirProperties {
  private FhirSystems systems;
  private FhirProfiles profiles;
  private Fhir fhir;

  public FhirProperties(ToFhirProperties toFhirProperties) {
    this.fhir = toFhirProperties.fhir();
  }

  public Fhir fhir() {
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
