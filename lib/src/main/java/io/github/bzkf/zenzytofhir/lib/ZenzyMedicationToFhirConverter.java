package io.github.bzkf.zenzytofhir.lib;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Converter that transforms Zenzy medication records into FHIR bundles.
 */
public class ZenzyMedicationToFhirConverter {

    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * Convert a Zenzy medication record to a FHIR Bundle (as JSON).
     * 
     * @param medication the Zenzy medication record
     * @return a JSON object representing the FHIR Bundle
     */
    public static ObjectNode convertToFhirBundle(ZenzyMedication medication) {
        if (medication == null) {
            throw new IllegalArgumentException("Medication cannot be null");
        }
        
        ObjectNode bundle = mapper.createObjectNode();
        bundle.put("resourceType", "Bundle");
        bundle.put("type", "transaction");
        bundle.put("id", "medication-bundle-" + medication.autoNr());
        
        // Add metadata about the medication
        ObjectNode entry = mapper.createObjectNode();
        entry.put("id", "medication-" + medication.autoNr());
        entry.put("autoNr", medication.autoNr());
        entry.put("wirkstoff", medication.wirkstoff());
        entry.put("dosierung", medication.dosierung());
        entry.put("applikationsArt", medication.applikationsArt());
        
        var entries = mapper.createArrayNode();
        entries.add(entry);
        bundle.set("entry", entries);
        
        return bundle;
    }
}
