package io.github.bzkf.zenzytofhir.mappings;

import org.hl7.fhir.r4.model.Coding;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "fhir")
public class FhirProperties {
  // TODO: need to figure out how to use a record for systems as well
  private FhirSystems systems;
  private Codings codings;

  public FhirSystems getSystems() {
    return systems;
  }

  public void setSystems(FhirSystems systems) {
    this.systems = systems;
  }

  public Codings getCodings() {
    return codings;
  }

  public void setCodings(Codings codings) {
    this.codings = codings;
  }

  public static record Codings(Coding loinc, Coding snomed, Coding ops) {
    @Override
    public Coding loinc() {
      // return a fresh copy, otherwise the original instance will be modified
      return loinc.copy();
    }

    @Override
    public Coding snomed() {
      return snomed.copy();
    }

    @Override
    public Coding ops() {
      return ops.copy();
    }
  }

  public record FhirSystems(FhirIdentifiers identifiers, String ucum) {}

  public record FhirIdentifiers(
      String patientId,
      String zenzyTherapieAutoNr,
      String zenzyHerstellungsId,
      String zenzyWirkstoffId,
      String zenzyTraegerloesungId) {}
}
