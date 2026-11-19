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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ToCodingMapper {
  private static final Logger LOG = LoggerFactory.getLogger(ToCodingMapper.class);

  // REASON_STUDY_PLACEBO in mappings/map_to_atc.py
  static final String REVIEW_REASON_PLACEBO = "blinded study medication (placebo)";

  private final Map<String, MappedApplikationsart> applikationsartMapping = new HashMap<>();
  private final Map<String, MappedTraegerloesung> traegerloesungMapping = new HashMap<>();
  // raw wirkstoff name -> normalized name
  private final Map<String, String> wirkstoffNormalization = new HashMap<>();
  // (raw or normalized) wirkstoff name -> codes
  private final Map<String, MappedWirkstoff> wirkstoffMapping = new HashMap<>();

  // output files of mappings/map_to_atc.py, not bundled for license reasons
  @Value("${zenzy-to-fhir.mappings.wirkstoff.normalized-names-path:}")
  private String normalizedNamesPath;

  @Value("${zenzy-to-fhir.mappings.wirkstoff.atc-snomed-mapping-path:}")
  private String atcSnomedMappingPath;

  @Value("${zenzy-to-fhir.mappings.wirkstoff.extra-path:}")
  private String extraWirkstoffPath;

  @PostConstruct
  public void init() throws IOException {
    var mapper =
        CsvMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();

    var schema = CsvSchema.emptySchema().withHeader().withColumnSeparator(';');
    // the output files of mappings/map_to_atc.py are read as they are (comma-separated)
    var mappingOutputSchema = CsvSchema.emptySchema().withHeader();

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

    // later files take precedence: bundled < mapping output < extra mappings
    resource = new ClassPathResource("mappings/wirkstoffe.csv");
    values =
        mapper
            .readerFor(MappedWirkstoff.class)
            .with(schema)
            .readValues(resource.getInputStream())
            .readAll();
    for (var value : values) {
      var row = (MappedWirkstoff) value;
      wirkstoffMapping.put(row.wirkstoff(), row);
    }

    var normalizedNamesFile = configuredFile(normalizedNamesPath, "normalized-names-path");
    if (normalizedNamesFile.isPresent()) {
      values =
          mapper
              .readerFor(NormalizedName.class)
              .with(mappingOutputSchema)
              .readValues(Files.newInputStream(normalizedNamesFile.get()))
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
    }

    var atcSnomedMappingFile = configuredFile(atcSnomedMappingPath, "atc-snomed-mapping-path");
    if (atcSnomedMappingFile.isPresent()) {
      values =
          mapper
              .readerFor(AtcSnomedMapping.class)
              .with(mappingOutputSchema)
              .readValues(Files.newInputStream(atcSnomedMappingFile.get()))
              .readAll();
      for (var value : values) {
        var row = (AtcSnomedMapping) value;
        if (row.hasCode()) {
          wirkstoffMapping.put(row.value(), row.toMappedWirkstoff());
        }
      }
    }

    var extraFile = configuredFile(extraWirkstoffPath, "extra-path");
    if (extraFile.isPresent()) {
      values =
          mapper
              .readerFor(MappedWirkstoff.class)
              .with(schema)
              .readValues(Files.newInputStream(extraFile.get()))
              .readAll();
      for (var value : values) {
        var row = (MappedWirkstoff) value;
        wirkstoffMapping.put(row.wirkstoff(), row);
      }
    }
  }

  private static Optional<Path> configuredFile(String path, String property) {
    if (!StringUtils.hasText(path)) {
      return Optional.empty();
    }
    var file = Paths.get(path);
    if (!Files.exists(file)) {
      LOG.warn(
          "zenzy-to-fhir.mappings.wirkstoff.{} {} does not exist, ignoring it", property, path);
      return Optional.empty();
    }
    return Optional.of(file);
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
