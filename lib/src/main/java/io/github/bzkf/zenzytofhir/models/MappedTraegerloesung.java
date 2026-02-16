package io.github.bzkf.zenzytofhir.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MappedTraegerloesung(
    String traegerloesung,
    String snomedCode,
    String snomedDisplay,
    String atcCode,
    String atcDisplay) {}
