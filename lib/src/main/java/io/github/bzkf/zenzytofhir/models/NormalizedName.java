package io.github.bzkf.zenzytofhir.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A row of {@code mappings/normalized-names.csv}, written by {@code mappings/map_to_atc.py}: a raw
 * Zenzy wirkstoff name and one normalized name it was cleaned to.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NormalizedName(String rawValue, String value, String reviewReason) {}
