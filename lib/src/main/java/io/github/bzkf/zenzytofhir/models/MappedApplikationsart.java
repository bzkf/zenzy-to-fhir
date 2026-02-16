package io.github.bzkf.zenzytofhir.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MappedApplikationsart(
    String applikationsart,
    String routeSnomedCode,
    String routeSnomedDisplay,
    String methodSnomedCode,
    String methodSnomedDisplay,
    Double duration,
    Double durationMax,
    String durationUcumUnit,
    String comment) {}
