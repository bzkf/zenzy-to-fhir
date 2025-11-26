package io.github.bzkf.zenzytofhir;

import io.github.bzkf.zenzytofhir.lib.FhirProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DisplayName("Zenzy to FHIR Application Tests")
class ZenzyToFhirApplicationTests {

	@Test
	@DisplayName("Application context should load successfully")
	void contextLoads() {
	}

	@Autowired(required = false)
	private FhirProperties fhirProperties;

	@Test
	@DisplayName("Should access FhirProperties from lib module")
	void testFhirPropertiesFromLibModule() {
		assertNotNull(fhirProperties, "FhirProperties should be accessible from lib module");
		assertEquals("http://loinc.org", fhirProperties.system());
	}

	@Test
	@DisplayName("Should get correct FHIR system value from library configuration")
	void testFhirSystemValueInheritance() {
		assertNotNull(fhirProperties);
		assertTrue(fhirProperties.system().startsWith("http://"));
		assertTrue(fhirProperties.system().contains("loinc"));
	}

}
