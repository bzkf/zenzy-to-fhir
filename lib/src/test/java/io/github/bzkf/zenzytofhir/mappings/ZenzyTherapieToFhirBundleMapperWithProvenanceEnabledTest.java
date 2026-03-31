package io.github.bzkf.zenzytofhir.mappings;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.bzkf.zenzytofhir.ProfileTestConfig;
import io.github.bzkf.zenzytofhir.mappings.config.ZenzyToFhirConfig;
import io.github.bzkf.zenzytofhir.models.ZenzyTherapie;
import io.github.dizuker.tofhir.config.ToFhirAutoConfiguration;
import java.io.IOException;
import org.approvaltests.Approvals;
import org.approvaltests.core.Scrubber;
import org.approvaltests.scrubbers.RegExScrubber;
import org.approvaltests.scrubbers.Scrubbers;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    classes = {
      ToFhirAutoConfiguration.class,
      FhirProperties.class,
      HergestellteMedicationMapper.class,
      TraegerLoesungMedicationMapper.class,
      MedicationRequestMapper.class,
      ProfileTestConfig.class,
      ToCodingMapper.class,
      WirkstoffMedicationMapper.class,
      ZenzyTherapieToFhirBundleMapper.class,
      DeviceMapper.class,
    },
    properties = {
      "zenzy-to-fhir.version=1.0.0-test",
      "zenzy-to-fhir.mappings.provenance.enabled=true"
    })
@EnableConfigurationProperties(ZenzyToFhirConfig.class)
class ZenzyTherapieToFhirBundleMapperWithProvenanceEnabledTest {
  private static final FhirContext fhirContext = FhirContext.forR4();
  public static final Scrubber FHIR_DATE_TIME_SCRUBBER =
      Scrubbers.scrubAll(
          new RegExScrubber(
              "\"occurredDateTime\": \"(.*)\"", "\"occurredDateTime\": \"2000-01-01T11:11:11Z\""),
          new RegExScrubber("\"recorded\": \"(.*)\"", "\"recorded\": \"2000-01-01T11:11:11Z\""));

  @Autowired private ZenzyTherapieToFhirBundleMapper sut;

  @ParameterizedTest
  @CsvSource({
    "therapie-1.json",
    "therapie-2.json",
    "therapie-3.json",
  })
  void map_withGivenZenzyTherapieRecord_shouldCreateExpectedFhirBundle(String sourceFile)
      throws IOException {
    final var recordStream = this.getClass().getClassLoader().getResource("fixtures/" + sourceFile);
    var mapper =
        new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS);
    final var record = mapper.readValue(recordStream.openStream(), ZenzyTherapie.class);

    var mapped = sut.map(record);

    var fhirParser = fhirContext.newJsonParser().setPrettyPrint(true);
    var fhirJson = fhirParser.encodeResourceToString(mapped.get());
    Approvals.verify(
        fhirJson,
        Approvals.NAMES
            .withParameters(sourceFile)
            .withScrubber(FHIR_DATE_TIME_SCRUBBER)
            .forFile()
            .withExtension(".fhir.json"));
  }
}
