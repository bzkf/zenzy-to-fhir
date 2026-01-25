package io.github.bzkf.zenzytofhir.models;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Date;

public record ZenzyTherapie(
    @JsonProperty("AUTONR") Integer autoNr,
    @JsonProperty("NR") Integer nr,
    @JsonProperty("APPLIKATIONSDATUM") Long applikationsDatum,
    @JsonProperty("APPLIKATIONSZEIT") String applikationsZeit,
    @JsonProperty("APPLIKATIONSZEITPUNKT") Date applikationsZeitpunkt,
    @JsonProperty("APPLIKATIONSART") String applikationsArt,
    @JsonProperty("STATUS") String status,
    @JsonProperty("THERAPIENUMMER") Integer therapieNummer,
    @JsonProperty("HERSTELLUNGSID") String herstellungsId,
    @JsonProperty("HERSTELLUNGSZEITPUNKT")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd.MM.yyyy, hh:mm")
        Date herstellungsZeitpunkt,
    @JsonProperty("RETOUREHERSTELLUNGSID") String retourHerstellungsId,
    @JsonProperty("INAPOTHEKEZUBEREITEN") Integer inApothekezubereiten,
    @JsonProperty("TRAEGERLOESUNG") String traegerLoesung,
    @JsonProperty("GESAMTVOLUMEN") String gesamtvolumen,
    @JsonProperty("GESAMTVOLUMEN_NUMERIC") Double gesamtvolumenNumeric,
    @JsonProperty("ADVOLUMEN") Integer adVolumen,
    @JsonProperty("WIRKSTOFF") String wirkstoff,
    @JsonProperty("DOSIERUNG") String dosierung,
    @JsonProperty("DOSIERUNGEINHEIT") String dosierungEinheit,
    @JsonProperty("DOSIERUNGSART") String dosierungsArt,
    @JsonProperty("DOSIS") String dosis,
    @JsonProperty("KISPATIENTENID") String kisPatientenId) {}
