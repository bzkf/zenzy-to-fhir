package io.github.bzkf.zenzytofhir.mappings;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.bzkf.zenzytofhir.models.ZenzyTherapie;
import java.io.IOException;
import org.approvaltests.Approvals;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = {FhirProperties.class})
@EnableConfigurationProperties
@ConfigurationPropertiesScan
public class ZenzyTherapieToFhirBundleMapperTest {
  private static final FhirContext fhirContext = FhirContext.forR4();
  private static ZenzyTherapieToFhirBundleMapper sut;

  @BeforeAll
  static void beforeAll(@Autowired FhirProperties fhirProps) {
    sut =
        new ZenzyTherapieToFhirBundleMapper(
            fhirProps, t -> new Reference("Patient/" + t.kisPatientenId()));
  }

  @ParameterizedTest
  @CsvSource({
    "therapie-1.json",
    "therapie-2.json",
    "therapie-3.json",
    "therapie-4.json",
  })
  void map_withGivenZenzyTherapieRecord_shouldCreateExpectedFhirBundle(String sourceFile)
      throws StreamReadException, DatabindException, IOException {
    final var recordStream = this.getClass().getClassLoader().getResource("fixtures/" + sourceFile);
    var mapper =
        new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS);
    final var record = mapper.readValue(recordStream.openStream(), ZenzyTherapie.class);

    var mapped = sut.map(record);

    var fhirParser = fhirContext.newJsonParser().setPrettyPrint(true);
    var fhirJson = fhirParser.encodeResourceToString(mapped);
    Approvals.verify(
        fhirJson, Approvals.NAMES.withParameters(sourceFile).forFile().withExtension(".fhir.json"));
  }
}
