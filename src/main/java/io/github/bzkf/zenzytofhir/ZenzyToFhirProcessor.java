package io.github.bzkf.zenzytofhir;

import static net.logstash.logback.argument.StructuredArguments.kv;

import io.github.bzkf.zenzytofhir.mappings.ZenzyTherapieToFhirBundleMapper;
import io.github.bzkf.zenzytofhir.models.ZenzyTherapie;
import java.util.function.Function;
import org.hl7.fhir.r4.model.Bundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

@Service
public class ZenzyToFhirProcessor {
  private static final Logger LOG = LoggerFactory.getLogger(ZenzyToFhirProcessor.class);
  private ZenzyTherapieToFhirBundleMapper mapper;

  public ZenzyToFhirProcessor(ZenzyTherapieToFhirBundleMapper mapper) {
    super();
    this.mapper = mapper;
  }

  @Bean
  Function<Message<ZenzyTherapie>, Message<Bundle>> sink() {
    return message -> {
      if (message == null) {
        LOG.warn("message is null. Ignoring.");
        return null;
      }

      var record = message.getPayload();

      LOG.debug("Processing single therapie message {}", kv("nr", record.nr()));

      var mapped = mapper.map(record);
      if (mapped.isPresent()) {
        var messageKey = mapped.get().getId();

        var messageBuilder =
            MessageBuilder.withPayload(mapped.get()).setHeader(KafkaHeaders.KEY, messageKey);

        return messageBuilder.build();
      } else {
        return null;
      }
    };
  }
}
