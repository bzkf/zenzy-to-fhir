package io.github.bzkf.zenzytofhir.mappings;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import io.github.bzkf.zenzytofhir.models.AtcSnomedMapping;
import io.github.bzkf.zenzytofhir.models.MappedApplikationsart;
import io.github.bzkf.zenzytofhir.models.MappedTraegerloesung;
import io.github.bzkf.zenzytofhir.models.MappedWirkstoff;
import io.github.bzkf.zenzytofhir.models.NormalizedName;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ToCodingMapper {
  // copies of the output files of mappings/map_to_atc.py, see mappings/README.md
  static final String NORMALIZED_NAMES_FILE = "mappings/normalized-names.csv";
  static final String ATC_SNOMED_MAPPING_FILE = "mappings/atc-snomed-mapping.csv";
  // REASON_STUDY_PLACEBO in mappings/map_to_atc.py
  static final String REVIEW_REASON_PLACEBO = "blinded study medication (placebo)";

  private final Map<String, MappedApplikationsart> applikationsartMapping = new HashMap<>();
  private final Map<String, MappedTraegerloesung> traegerloesungMapping = new HashMap<>();
  // raw wirkstoff name -> normalized name
  private final Map<String, String> wirkstoffNormalization = new HashMap<>();
  // normalized wirkstoff name -> codes
  private final Map<String, MappedWirkstoff> wirkstoffMapping = new HashMap<>();

  @Value("${zenzy-to-fhir.mappings.wirkstoff.extra-path:}")
  private String extraWirkstoffPath;

  @PostConstruct
  public void init() throws IOException {
    var mapper =
        CsvMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();

    var schema = CsvSchema.emptySchema().withHeader().withColumnSeparator(';');

    var resource = new ClassPathResource("mappings/applikationsart.csv");
    var values =
        mapper
            .readerFor(MappedApplikationsart.class)
            .with(schema)
            .readValues(resource.getInputStream())
            .readAll();

    for (var value : values) {
      var row = (MappedApplikationsart) value;
      applikationsartMapping.put(row.applikationsart(), row);
    }

    resource = new ClassPathResource("mappings/traegerloesung.csv");
    values =
        mapper
            .readerFor(MappedTraegerloesung.class)
            .with(schema)
            .readValues(resource.getInputStream())
            .readAll();
    for (var value : values) {
      var row = (MappedTraegerloesung) value;
      traegerloesungMapping.put(row.traegerloesung(), row);
    }

    // both files are the unchanged output of mappings/map_to_atc.py (comma-separated)
    var outputSchema = CsvSchema.emptySchema().withHeader();

    resource = new ClassPathResource(NORMALIZED_NAMES_FILE);
    values =
        mapper
            .readerFor(NormalizedName.class)
            .with(outputSchema)
            .readValues(resource.getInputStream())
            .readAll();
    var namesByRawValue = new LinkedHashMap<String, List<NormalizedName>>();
    for (var value : values) {
      var row = (NormalizedName) value;
      namesByRawValue.computeIfAbsent(row.rawValue(), k -> new ArrayList<>()).add(row);
    }
    for (var entry : namesByRawValue.entrySet()) {
      var names = entry.getValue();
      // a Medication has a single code: raw names that split into several substances
      // ("A + B") can't be represented
      if (names.size() != 1) {
        continue;
      }
      // the patient may have received the placebo, not the substance
      if (REVIEW_REASON_PLACEBO.equals(names.getFirst().reviewReason())) {
        continue;
      }
      wirkstoffNormalization.put(entry.getKey(), names.getFirst().value());
    }

    resource = new ClassPathResource(ATC_SNOMED_MAPPING_FILE);
    values =
        mapper
            .readerFor(AtcSnomedMapping.class)
            .with(outputSchema)
            .readValues(resource.getInputStream())
            .readAll();
    for (var value : values) {
      var row = (AtcSnomedMapping) value;
      if (row.hasCode()) {
        wirkstoffMapping.put(row.value(), row.toMappedWirkstoff());
      }
    }

    // Load extra wirkstoff mappings if path is configured
    if (StringUtils.hasText(extraWirkstoffPath)) {
      var extraPath = Paths.get(extraWirkstoffPath);
      if (Files.exists(extraPath)) {
        values =
            mapper
                .readerFor(MappedWirkstoff.class)
                .with(schema)
                .readValues(Files.newInputStream(extraPath))
                .readAll();
        for (var value : values) {
          var row = (MappedWirkstoff) value;
          wirkstoffMapping.put(row.wirkstoff(), row);
        }
      }
    }
  }

  public Optional<MappedApplikationsart> mapApplikationsart(@NonNull String applikationsartText) {
    var applikationsart = applikationsartMapping.get(applikationsartText.trim());
    return Optional.ofNullable(applikationsart);
  }

  public Optional<MappedTraegerloesung> mapTraegerloesung(@NonNull String traegerloesungText) {
    var traegerloesung = traegerloesungMapping.get(traegerloesungText.trim());
    return Optional.ofNullable(traegerloesung);
  }

  /**
   * Maps a raw wirkstoff name. An entry for the raw name itself wins (e.g. from the extra mappings
   * file), otherwise the name is normalized (e.g. "Avastin*" to "Avastin") and the normalized name
   * is looked up for its codes.
   */
  public Optional<MappedWirkstoff> mapWirkstoff(@NonNull String wirkstoffText) {
    var raw = wirkstoffText.trim();
    var byRawName = wirkstoffMapping.get(raw);
    if (byRawName != null) {
      return Optional.of(byRawName);
    }
    var normalized = wirkstoffNormalization.get(raw);
    return normalized == null
        ? Optional.empty()
        : Optional.ofNullable(wirkstoffMapping.get(normalized));
  }
}
