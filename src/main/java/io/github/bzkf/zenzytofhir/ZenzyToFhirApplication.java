package io.github.bzkf.zenzytofhir;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties
public class ZenzyToFhirApplication {

  public static void main(String[] args) {
    SpringApplication.run(ZenzyToFhirApplication.class, args);
  }
}
