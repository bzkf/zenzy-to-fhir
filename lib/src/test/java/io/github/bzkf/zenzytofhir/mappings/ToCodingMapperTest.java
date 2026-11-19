package io.github.bzkf.zenzytofhir.mappings;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Uses fixtures in the output format of mappings/map_to_atc.py, since its real output is not
 * bundled.
 */
class ToCodingMapperTest {
  private static final String FIXTURES = "src/test/resources/fixtures/";
  private static final String SNOMED_VERSION =
      "http://snomed.info/sct/11000274103/version/20260815";

  private static final ToCodingMapper sut = new ToCodingMapper();

  @BeforeAll
  static void setUp() throws IOException {
    configure(sut, FIXTURES + "normalized-names.csv", FIXTURES + "atc-snomed-mapping.csv");
    ReflectionTestUtils.setField(sut, "extraWirkstoffPath", FIXTURES + "wirkstoffe-extra-raw.csv");
    sut.init();
  }

  private static void configure(ToCodingMapper mapper, String names, String mapping) {
    ReflectionTestUtils.setField(mapper, "normalizedNamesPath", names);
    ReflectionTestUtils.setField(mapper, "atcSnomedMappingPath", mapping);
  }

  @ParameterizedTest
  @CsvSource({
    // trailing marker removed, then resolved as a brand name
    "Avastin*, L01FG01, 409406007",
    // route moved out of the name
    "Trastuzumab subcutan, L01FD01, 387003001",
    // study label moved out of the name
    "Durvalumab/CheckRad, L01FF03, 735230005",
    // surrounding whitespace is ignored
    "'  Avastin*  ', L01FG01, 409406007",
  })
  void mapWirkstoff_withNormalizedName_shouldUseTheCodesOfTheNormalizedName(
      String raw, String atcCode, String snomedCode) {
    var mapped = sut.mapWirkstoff(raw);

    assertThat(mapped).isPresent();
    assertThat(mapped.get().atcCode()).isEqualTo(atcCode);
    assertThat(mapped.get().snomedCode()).isEqualTo(snomedCode);
    assertThat(mapped.get().snomedVersion()).isEqualTo(SNOMED_VERSION);
  }

  @Test
  void mapWirkstoff_withPlaceboBlindedStudyMedication_shouldNotMap() {
    // the patient may have received the placebo, not the substance
    assertThat(sut.mapWirkstoff("Atezolizumab/Placebo")).isEmpty();
  }

  @Test
  void mapWirkstoff_withCombination_shouldNotMap() {
    // a Medication has a single code
    assertThat(sut.mapWirkstoff("Nivolumab + Relatlimab")).isEmpty();
  }

  @Test
  void mapWirkstoff_withNormalizedNameWithoutCodes_shouldNotMap() {
    assertThat(sut.mapWirkstoff("Relatlimab, ungeprüft")).isEmpty();
  }

  @Test
  void mapWirkstoff_withNameInMappingFile_shouldPreferItOverTheBundledMapping() {
    // also in the bundled wirkstoffe.csv, which has no SNOMED CT version
    var mapped = sut.mapWirkstoff("Fluorouracil");
    assertThat(mapped).isPresent();
    assertThat(mapped.get().snomedVersion()).isEqualTo(SNOMED_VERSION);
  }

  @Test
  void mapWirkstoff_withNameOnlyInBundledMapping_shouldUseIt() {
    var mapped = sut.mapWirkstoff("Methotrexat");
    assertThat(mapped).isPresent();
    assertThat(mapped.get().atcCode()).isEqualTo("L01BA01");
    // the configured default version is used
    assertThat(mapped.get().snomedVersion()).isNull();
  }

  @Test
  void mapWirkstoff_withExtraEntryForRawName_shouldPreferItOverNormalization() {
    // "Testwirkstoff*" would be normalized to "Testwirkstoff", which has no entry
    var mapped = sut.mapWirkstoff("Testwirkstoff*");
    assertThat(mapped).isPresent();
    assertThat(mapped.get().atcCode()).isEqualTo("L01BC02");
  }

  @Test
  void mapWirkstoff_withUnknownName_shouldNotMap() {
    assertThat(sut.mapWirkstoff("KOMMENTAR !!!")).isEmpty();
  }

  @Test
  void init_withMissingMappingFiles_shouldOnlyUseTheBundledMapping() throws IOException {
    var mapper = new ToCodingMapper();
    configure(mapper, FIXTURES + "does-not-exist.csv", FIXTURES + "does-not-exist.csv");
    mapper.init();

    assertThat(mapper.mapWirkstoff("Avastin*")).isEmpty();
    assertThat(mapper.mapWirkstoff("Fluorouracil")).isPresent();
  }
}
