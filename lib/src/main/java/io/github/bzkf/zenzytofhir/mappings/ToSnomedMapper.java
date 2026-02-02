package io.github.bzkf.zenzytofhir.mappings;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import io.github.bzkf.zenzytofhir.models.MappedApplikationsart;
import io.github.bzkf.zenzytofhir.models.MappedTraegerloesung;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.NonNull;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
public class ToSnomedMapper {
  private final Map<String, MappedApplikationsart> applikationsartMapping = new HashMap<>();
  private final Map<String, MappedTraegerloesung> traegerloesungMapping = new HashMap<>();

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
  }

  public Optional<MappedApplikationsart> mapApplikationsart(@NonNull String applikationsartText) {
    var applikationsart = applikationsartMapping.get(applikationsartText.trim());
    return Optional.ofNullable(applikationsart);
  }

  public Optional<MappedTraegerloesung> mapTraegerloesung(@NonNull String traegerloesungText) {
    var traegerloesung = traegerloesungMapping.get(traegerloesungText.trim());
    return Optional.ofNullable(traegerloesung);
  }
}
