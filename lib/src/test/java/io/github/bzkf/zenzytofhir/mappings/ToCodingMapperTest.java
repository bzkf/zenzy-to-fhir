package io.github.bzkf.zenzytofhir.mappings;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ToCodingMapperTest {
  private static final ToCodingMapper sut = new ToCodingMapper();

  @BeforeAll
  static void setUp() throws IOException {
    sut.init();
  }

  @ParameterizedTest
  @CsvSource({
    // looked up as is
    "Fluorouracil, L01BC02, 387172005",
    // trailing marker removed, then resolved as a brand name
    "Avastin*, L01FG01, 409406007",
    // route moved out of the name
    "Trastuzumab subcutan, L01FD01, 387003001",
    // study label moved out of the name
    "Durvalumab/CheckRad, L01FF03, 735230005",
    // surrounding whitespace is ignored
    "'  Avastin*  ', L01FG01, 409406007",
  })
  void mapWirkstoff_shouldNormalizeTheRawNameBeforeLookingUpItsCodes(
      String raw, String atcCode, String snomedCode) {
    var mapped = sut.mapWirkstoff(raw);

    assertThat(mapped).isPresent();
    assertThat(mapped.get().atcCode()).isEqualTo(atcCode);
    assertThat(mapped.get().snomedCode()).isEqualTo(snomedCode);
  }

  @Test
  void mapWirkstoff_withPlaceboBlindedStudyMedication_shouldNotMap() {
    // the patient may have received the placebo, not the substance
    assertThat(sut.mapWirkstoff("Atezolizumab/Placebo")).isEmpty();
  }

  @Test
  void mapWirkstoff_withUnknownName_shouldNotMap() {
    assertThat(sut.mapWirkstoff("KOMMENTAR !!!")).isEmpty();
  }
}
