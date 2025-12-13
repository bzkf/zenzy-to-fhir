package io.github.bzkf.zenzytofhir.lib;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Java record representing a Zenzy medication administration record.
 * Deserialized from JSON test data.
 */
public record ZenzyMedication(
    @JsonProperty("AUTONR")
    Integer autoNr,
    
    @JsonProperty("NR")
    Integer nr,
    
    @JsonProperty("APPLIKATIONSDATUM")
    Long applikationsDatum,
    
    @JsonProperty("APPLIKATIONSZEIT")
    String applikationsZeit,
    
    @JsonProperty("APPLIKATIONSZEITPUNKT")
    Long applikationsZeitpunkt,
    
    @JsonProperty("APPLIKATIONSART")
    String applikationsArt,
    
    @JsonProperty("STATUS")
    String status,
    
    @JsonProperty("THERAPIENUMMER")
    Integer therapieNummer,
    
    @JsonProperty("HERSTELLUNGSID")
    String herstellungsId,
    
    @JsonProperty("HERSTELLUNGSZEITPUNKT")
    String herstellungsZeitpunkt,
    
    @JsonProperty("RETOUREHERSTELLUNGSID")
    String retourHerstellungsId,
    
    @JsonProperty("INAPOTHEKEZUBEREITEN")
    Integer inApothekezubereiten,
    
    @JsonProperty("TRAEGERLOESUNG")
    String traegerLoesung,
    
    @JsonProperty("GESAMTVOLUMEN")
    String gesamtvolumen,
    
    @JsonProperty("GESAMTVOLUMEN_NUMERIC")
    Double gesamtvolumenNumeric,
    
    @JsonProperty("ADVOLUMEN")
    Integer adVolumen,
    
    @JsonProperty("WIRKSTOFF")
    String wirkstoff,
    
    @JsonProperty("DOSIERUNG")
    String dosierung,
    
    @JsonProperty("DOSIERUNGEINHEIT")
    String dosierungEinheit,
    
    @JsonProperty("DOSIERUNGSART")
    String dosierungsArt,
    
    @JsonProperty("DOSIS")
    String dosis,
    
    @JsonProperty("KISPATIENTENID")
    String kisPatientenId
) {
}
