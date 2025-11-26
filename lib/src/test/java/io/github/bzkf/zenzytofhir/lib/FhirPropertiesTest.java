package io.github.bzkf.zenzytofhir.lib;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = FhirPropertiesTest.TestConfig.class)
@TestPropertySource(properties = "fhir.system=http://loinc.org")
@DisplayName("FhirProperties Configuration Tests")
class FhirPropertiesTest {

    @org.springframework.boot.autoconfigure.SpringBootApplication
    @EnableConfigurationProperties(FhirProperties.class)
    public static class TestConfig {
    }

    @Autowired(required = false)
    private FhirProperties fhirProperties;

    @Test
    @DisplayName("Should bind FHIR system property correctly")
    void testFhirSystemPropertyBinding() {
        assertNotNull(fhirProperties, "FhirProperties should be autowired");
        assertEquals("http://loinc.org", fhirProperties.system());
    }

    @Test
    @DisplayName("Should load FHIR system from application-fhir.yaml")
    void testFhirSystemFromYamlFile() {
        assertNotNull(fhirProperties, "FhirProperties should be loaded from YAML");
        assertTrue(fhirProperties.system().contains("loinc"));
        assertEquals("http://loinc.org", fhirProperties.system());
    }
}
