package io.github.bzkf.zenzytofhir;

import io.github.bzkf.zenzytofhir.mappings.FhirProperties;
import io.github.bzkf.zenzytofhir.models.ZenzyTherapie;
import java.util.function.Function;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.Validate;
import org.hl7.fhir.r4.model.Reference;
import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

@Service
public class PatientReferenceGenerator {
  private final @NonNull FhirProperties fhirProperties;

  public PatientReferenceGenerator(FhirProperties fhirProperties) {
    this.fhirProperties = fhirProperties;
  }

  @Bean
  Function<ZenzyTherapie, Reference> getPatientReferenceGenerationFunction() {
    return p -> {
      Validate.notBlank(p.kisPatientenId());

      var system = fhirProperties.getSystems().identifiers().patientId();
      var value = p.kisPatientenId();
      var digest = DigestUtils.sha256Hex(system + "|" + value);
      return new Reference("Patient/" + digest);
    };
  }
}
