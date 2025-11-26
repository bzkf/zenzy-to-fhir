package io.github.bzkf.zenzytofhir.lib;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ZenzyToFhirConverter Tests")
class ZenzyToFhirConverterTest {

    @Test
    @DisplayName("Should convert valid Zenzy data to FHIR format")
    void testConvertValidZenzyData() {
        String zenzyData = "sample zenzy data";
        String result = ZenzyToFhirConverter.convertToFhir(zenzyData);
        
        assertNotNull(result);
        assertTrue(result.contains("resourceType"));
        assertTrue(result.contains("Bundle"));
    }

    @Test
    @DisplayName("Should return valid JSON structure")
    void testReturnValidJsonStructure() {
        String result = ZenzyToFhirConverter.convertToFhir("test data");
        
        assertTrue(result.startsWith("{"));
        assertTrue(result.endsWith("}"));
    }

    @Test
    @DisplayName("Should throw exception for null input")
    void testThrowExceptionForNullInput() {
        assertThrows(IllegalArgumentException.class, () -> {
            ZenzyToFhirConverter.convertToFhir(null);
        });
    }

    @Test
    @DisplayName("Should throw exception for empty input")
    void testThrowExceptionForEmptyInput() {
        assertThrows(IllegalArgumentException.class, () -> {
            ZenzyToFhirConverter.convertToFhir("");
        });
    }

    @Test
    @DisplayName("Should provide library version")
    void testGetLibraryVersion() {
        String version = ZenzyToFhirConverter.getLibraryVersion();
        
        assertNotNull(version);
        assertFalse(version.isEmpty());
        assertEquals("0.0.1-SNAPSHOT", version);
    }
}
