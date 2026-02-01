package io.github.bzkf.zenzytofhir.mappings;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import io.github.bzkf.zenzytofhir.models.Applikationsart;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.NonNull;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
public class ApplikationsartToSnomedMapper {
  private final Map<String, Applikationsart> applikationsartToSnomedCodings = new HashMap<>();

  @PostConstruct
  public void init() throws IOException {
    CsvMapper mapper =
        CsvMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();

    CsvSchema schema = CsvSchema.emptySchema().withHeader().withColumnSeparator(';');

    var or = mapper.readerFor(Applikationsart.class).with(schema);

    var resource = new ClassPathResource("mappings/applikationsart.csv");
    var values = or.readValues(resource.getInputStream()).readAll();

    for (var row : values) {
      var aa = (Applikationsart) row;
      applikationsartToSnomedCodings.put(aa.applikationsart(), aa);
    }
  }

  public Optional<Applikationsart> getCode(@NonNull String applikationsartText) {
    var applikationsart = applikationsartToSnomedCodings.get(applikationsartText.trim());
    return Optional.ofNullable(applikationsart);
  }
}
