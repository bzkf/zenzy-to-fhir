package io.github.bzkf.zenzytofhir;

import io.github.bzkf.zenzytofhir.models.ZenzyTherapie;
import java.util.function.Function;
import org.hl7.fhir.r4.model.Reference;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProfileTestConfig {

  @Bean
  public Function<ZenzyTherapie, Reference> patientReferenceGenerator() {
    return p -> {
      return new Reference("Patient/" + p.kisPatientenId());
    };
  }
}
