package io.github.bzkf.zenzytofhir.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** A raw Zenzy wirkstoff name and the normalized name used to look up its codes. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NormalizedWirkstoff(String rawValue, String value) {}
