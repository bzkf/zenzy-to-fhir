package io.github.bzkf.zenzytofhir.mappings;

import com.github.slugify.Slugify;
import java.util.Locale;

public class MappingUtils {

  public static final Slugify SLUGIFY =
      Slugify.builder().lowerCase(true).locale(Locale.GERMAN).build();

  private MappingUtils() {
    // Utility class
  }
}
