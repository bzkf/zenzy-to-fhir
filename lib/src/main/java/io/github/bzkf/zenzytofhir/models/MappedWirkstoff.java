package io.github.bzkf.zenzytofhir.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Codes of a wirkstoff. {@code snomedVersion} is the SNOMED CT version the code was looked up in;
 * if empty, the configured default version is used.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MappedWirkstoff(
    String wirkstoff,
    String atcCode,
    String atcDisplay,
    String snomedCode,
    String snomedDisplay,
    String snomedVersion) {}
