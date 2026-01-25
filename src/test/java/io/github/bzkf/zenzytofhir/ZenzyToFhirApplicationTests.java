package io.github.bzkf.zenzytofhir;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@DisplayName("Zenzy to FHIR Application Tests")
@ConfigurationPropertiesScan
@EnableConfigurationProperties
class ZenzyToFhirApplicationTests {

  @Test
  @DisplayName("Application context should load successfully")
  void contextLoads() {}
}
