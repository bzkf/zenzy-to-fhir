package io.github.bzkf.zenzytofhir.mappings;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Checks the lookup logic against the generated mapping files without depending on which names they
 * contain, since they change with every export.
 */
class ToCodingMapperTest {
  private static final ToCodingMapper sut = new ToCodingMapper();
  private static List<Map<String, String>> normalization;
  private static List<Map<String, String>> codes;

  @BeforeAll
  static void setUp() throws IOException {
    ReflectionTestUtils.setField(
        sut, "extraWirkstoffPath", "src/test/resources/fixtures/wirkstoffe-extra-raw.csv");
    sut.init();
    normalization = readCsv(ToCodingMapper.NORMALIZED_NAMES_FILE);
    codes =
        readCsv(ToCodingMapper.ATC_SNOMED_MAPPING_FILE).stream()
            .filter(row -> !row.get("atc_code").isEmpty() || !row.get("snomed_code").isEmpty())
            .toList();
  }

  private static List<Map<String, String>> readCsv(String path) throws IOException {
    var schema = CsvSchema.emptySchema().withHeader();
    try (MappingIterator<Map<String, String>> rows =
        new CsvMapper()
            .readerForMapOf(String.class)
            .with(schema)
            .readValues(new ClassPathResource(path).getInputStream())) {
      return rows.readAll();
    }
  }

  @Test
  void mapWirkstoff_withNormalizedName_shouldMapToCodesOfTheNormalizedName() {
    assertThat(normalization).isNotEmpty();
    var rowsPerRawValue =
        normalization.stream().collect(Collectors.groupingBy(row -> row.get("raw_value")));
    for (var row : normalization) {
      var raw = row.get("raw_value");
      var placebo = ToCodingMapper.REVIEW_REASON_PLACEBO.equals(row.get("review_reason"));
      if (rowsPerRawValue.get(raw).size() != 1 || placebo) {
        // combinations and placebo-blinded study medication are not normalized
        continue;
      }
      assertThat(sut.mapWirkstoff(raw)).as(raw).isEqualTo(sut.mapWirkstoff(row.get("value")));
    }
  }

  @Test
  void mapWirkstoff_withPlaceboBlindedStudyMedication_shouldNotMap() {
    var placebo =
        normalization.stream()
            .filter(row -> ToCodingMapper.REVIEW_REASON_PLACEBO.equals(row.get("review_reason")))
            .toList();
    assertThat(placebo).isNotEmpty();
    for (var row : placebo) {
      assertThat(sut.mapWirkstoff(row.get("raw_value"))).as(row.get("raw_value")).isEmpty();
    }
  }

  @Test
  void mapWirkstoff_withCodedName_shouldReturnItsCodesAndSnomedVersion() {
    assertThat(codes).isNotEmpty();
    for (var row : codes) {
      var mapped = sut.mapWirkstoff(row.get("value"));
      assertThat(mapped).as(row.get("value")).isPresent();
      assertThat(mapped.get().atcCode()).isEqualTo(row.get("atc_code"));
      assertThat(mapped.get().snomedCode()).isEqualTo(row.get("snomed_code"));
      if (!row.get("snomed_code").isEmpty()) {
        // the version must be written next to every SNOMED CT code
        assertThat(mapped.get().snomedVersion()).as(row.get("value")).isNotBlank();
      }
    }
  }

  @Test
  void mapWirkstoff_shouldOnlyUseSubstanceLevelAtcCodes() {
    for (var row : codes) {
      var atcCode = row.get("atc_code");
      if (!atcCode.isEmpty()) {
        assertThat(atcCode).as(row.get("value")).hasSize(7);
      }
    }
  }

  @Test
  void mapWirkstoff_shouldIgnoreSurroundingWhitespace() {
    var name = codes.getFirst().get("value");
    assertThat(sut.mapWirkstoff("  " + name + "  ")).isEqualTo(sut.mapWirkstoff(name));
  }

  @Test
  void mapWirkstoff_withExtraEntryForRawName_shouldPreferItOverNormalization() {
    // "Testwirkstoff*" would be normalized to "Testwirkstoff", which has no entry
    var mapped = sut.mapWirkstoff("Testwirkstoff*");
    assertThat(mapped).isPresent();
    assertThat(mapped.get().atcCode()).isEqualTo("L01BC02");
    // extra files may omit the version, then the configured default is used
    assertThat(mapped.get().snomedVersion()).isNull();
  }

  @Test
  void mapWirkstoff_withUnknownName_shouldNotMap() {
    assertThat(sut.mapWirkstoff("KOMMENTAR !!!")).isEmpty();
  }
}
