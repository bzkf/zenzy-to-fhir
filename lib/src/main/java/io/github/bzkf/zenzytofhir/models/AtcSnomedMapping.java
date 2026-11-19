package io.github.bzkf.zenzytofhir.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.util.StringUtils;

/**
 * A row of {@code mappings/atc-snomed-mapping.csv}, written by {@code mappings/map_to_atc.py}: the
 * codes selected for a normalized wirkstoff name. Empty codes were not selected (no match or
 * ambiguous).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AtcSnomedMapping(
    String value,
    String atcCode,
    String atcMeaning,
    String snomedCode,
    String snomedDisplay,
    String snomedVersion) {

  public boolean hasCode() {
    return StringUtils.hasText(atcCode) || StringUtils.hasText(snomedCode);
  }

  public MappedWirkstoff toMappedWirkstoff() {
    return new MappedWirkstoff(
        value, atcCode, atcMeaning, snomedCode, snomedDisplay, snomedVersion);
  }
}
