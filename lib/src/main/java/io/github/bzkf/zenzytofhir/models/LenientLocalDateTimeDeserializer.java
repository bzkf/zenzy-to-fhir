package io.github.bzkf.zenzytofhir.models;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LenientLocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {
  private static final Logger LOG = LoggerFactory.getLogger(LenientLocalDateTimeDeserializer.class);

  private static final DateTimeFormatter FORMATTER =
      DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm *");

  @Override
  public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
    String value = p.getValueAsString();

    if (value == null || value.isBlank()) {
      return null;
    }

    try {
      return LocalDateTime.parse(value, FORMATTER);
    } catch (DateTimeParseException e) {
      LOG.error("Failed to parse timestamp", e);
      return null; // swallow invalid timestamps
    }
  }
}
