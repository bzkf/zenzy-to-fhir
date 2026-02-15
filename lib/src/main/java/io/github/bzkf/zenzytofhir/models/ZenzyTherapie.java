package io.github.bzkf.zenzytofhir.models;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ZenzyTherapie(
    @JsonProperty("AUTONR") Integer autoNr,
    @JsonProperty("NR") Integer nr,
    @JsonProperty("APPLIKATIONSDATUM") Instant applikationsDatum,
    @JsonProperty("APPLIKATIONSZEIT") @JsonFormat(pattern = "HH:mm:ss") LocalTime applikationsZeit,
    @JsonProperty("APPLIKATIONSZEITPUNKT")
        @JsonFormat(without = {JsonFormat.Feature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS}, timezone = "Europe/Berlin")
        Instant applikationsZeitpunkt,
    @JsonProperty("APPLIKATIONSART") String applikationsart,
    @JsonProperty("STATUS") String status,
    @JsonProperty("THERAPIENUMMER") Integer therapieNummer,
    @JsonProperty("HERSTELLUNGSID") String herstellungsId,
    @JsonProperty("HERSTELLUNGSZEITPUNKT") @JsonFormat(pattern = "dd.MM.yyyy, HH:mm *")
        LocalDateTime herstellungsZeitpunkt,
    @JsonProperty("RETOUREHERSTELLUNGSID") String retourHerstellungsId,
    @JsonProperty("INAPOTHEKEZUBEREITEN") Integer inApothekezubereiten,
    @JsonProperty("TRAEGERLOESUNG") String traegerloesung,
    @JsonProperty("GESAMTVOLUMEN") String gesamtvolumen,
    @JsonProperty("GESAMTVOLUMEN_NUMERIC") Double gesamtvolumenNumeric,
    @JsonProperty("ADVOLUMEN") Integer adVolumen,
    @JsonProperty("WIRKSTOFF") String wirkstoff,
    @JsonProperty("DOSIERUNG") String dosierung,
    @JsonProperty("DOSIERUNGEINHEIT") String dosierungEinheit,
    @JsonProperty("DOSIERUNGSART") String dosierungsart,
    @JsonProperty("DOSIS") String dosis,
    @JsonProperty("KISPATIENTENID") String kisPatientenId) {}
