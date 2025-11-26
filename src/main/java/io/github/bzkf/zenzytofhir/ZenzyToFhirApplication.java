package io.github.bzkf.zenzytofhir;

import io.github.bzkf.zenzytofhir.lib.FhirProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(FhirProperties.class)
public class ZenzyToFhirApplication {

	public static void main(String[] args) {
		SpringApplication.run(ZenzyToFhirApplication.class, args);
	}

}

