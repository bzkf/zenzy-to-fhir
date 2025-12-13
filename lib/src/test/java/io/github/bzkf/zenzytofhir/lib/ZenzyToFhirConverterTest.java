package io.github.bzkf.zenzytofhir.lib;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Zenzy to FHIR Conversion Tests")
class ZenzyToFhirConverterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Should convert Zenzy medications from test.json to FHIR bundles")
    void testConvertZenzyMedicationsToFhirBundles() throws IOException {
        List<ZenzyMedication> medications = loadTestMedications();
        
        assertFalse(medications.isEmpty(), "Should load at least one medication from test.json");
        assertEquals(4, medications.size(), "Should load exactly 4 medications from test.json");
        
        List<String> bundles = new ArrayList<>();
        for (ZenzyMedication medication : medications) {
            ObjectNode bundle = ZenzyMedicationToFhirConverter.convertToFhirBundle(medication);
            String bundleJson = serializeBundleToJson(bundle);
            bundles.add(bundleJson);
            
            // Verify bundle structure
            assertNotNull(bundle);
            assertNotNull(bundle.get("id"));
            assertFalse(bundle.get("entry").isEmpty());
        }
        
        // Write snapshot for manual review
        String output = String.join("\n---\n", bundles);
        writeSnapshot(output, "zenzy-to-fhir-bundles.json");
    }

    @Test
    @DisplayName("Should convert first Zenzy medication to FHIR bundle")
    void testConvertFirstMedicationToFhirBundle() throws IOException {
        List<ZenzyMedication> medications = loadTestMedications();
        
        assertTrue(medications.size() > 0, "Should have at least one medication");
        ZenzyMedication firstMedication = medications.get(0);
        
        // Verify medication was parsed correctly
        assertNotNull(firstMedication.autoNr());
        assertEquals(1, firstMedication.autoNr());
        assertEquals("Infusion", firstMedication.applikationsArt());
        assertEquals("Fluorouracil", firstMedication.wirkstoff());
        
        // Convert to bundle
        ObjectNode bundle = ZenzyMedicationToFhirConverter.convertToFhirBundle(firstMedication);
        String bundleJson = serializeBundleToJson(bundle);
        
        // Write snapshot for manual review
        writeSnapshot(bundleJson, "zenzy-medication-bundle-1.json");
        
        // Verify bundle structure
        assertNotNull(bundle);
        assertNotNull(bundle.get("id"));
        assertTrue(bundle.get("id").asText().contains("medication-bundle-1"));
        assertEquals(1, bundle.get("entry").size());
    }

    private List<ZenzyMedication> loadTestMedications() throws IOException {
        List<ZenzyMedication> medications = new ArrayList<>();
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(getClass().getResourceAsStream("/test.json")))) {
            
            String line;
            StringBuilder jsonBuffer = new StringBuilder();
            
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                
                jsonBuffer.append(line);
                
                // Check if we have a complete JSON object (closing brace)
                if (line.endsWith("}")) {
                    ZenzyMedication medication = objectMapper.readValue(
                            jsonBuffer.toString(),
                            ZenzyMedication.class
                    );
                    medications.add(medication);
                    jsonBuffer = new StringBuilder();
                }
            }
        }
        
        return medications;
    }

    private String serializeBundleToJson(ObjectNode bundle) throws IOException {
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        return objectMapper.writeValueAsString(bundle);
    }

    private void writeSnapshot(String content, String filename) throws IOException {
        // Write to build directory for review
        Path snapshotDir = Paths.get("build/test-snapshots");
        Files.createDirectories(snapshotDir);
        Path snapshotFile = snapshotDir.resolve(filename);
        Files.writeString(snapshotFile, content);
    }
}
