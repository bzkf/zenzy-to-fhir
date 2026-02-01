package io.github.bzkf.zenzytofhir.mappings;

import static net.logstash.logback.argument.StructuredArguments.kv;

import io.github.bzkf.zenzytofhir.models.ZenzyTherapie;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.function.Function;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleType;
import org.hl7.fhir.r4.model.Bundle.HTTPVerb;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Dosage;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.MedicationRequest.MedicationRequestIntent;
import org.hl7.fhir.r4.model.MedicationRequest.MedicationRequestStatus;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.Timing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ZenzyTherapieToFhirBundleMapper {

  private final TraegerLoesungMapper traegerLoesungMapper;
  private final WirkstoffMapper wirkstoffMapper;
  private static final Logger LOG = LoggerFactory.getLogger(ZenzyTherapieToFhirBundleMapper.class);
  private static final ZoneId DEFAULT_ZONE_ID = ZoneId.of("Europe/Berlin");
  private final FhirProperties fhirProps;
  private final Function<ZenzyTherapie, Reference> patientReferenceGenerator;
  private final HergestellteMedicationMapper medicationMapper;

  public ZenzyTherapieToFhirBundleMapper(
      FhirProperties fhirProperties,
      Function<ZenzyTherapie, Reference> patientReferenceGenerator,
      HergestellteMedicationMapper medicationMapper,
      TraegerLoesungMapper traegerLoesungMapper,
      WirkstoffMapper wirkstoffMapper) {
    this.fhirProps = fhirProperties;
    this.patientReferenceGenerator = patientReferenceGenerator;
    this.medicationMapper = medicationMapper;
    this.traegerLoesungMapper = traegerLoesungMapper;
    this.wirkstoffMapper = wirkstoffMapper;
  }

  public Bundle map(ZenzyTherapie record) {
    LOG.debug("Mapping ZenzyTherapie record {} to FHIR", kv("nr", record.nr()));

    // Mapping logic to convert ZenzyTherapieRecord to FHIR Bundle goes here
    var medicationRequest = new MedicationRequest();

    var identifier =
        new Identifier()
            .setSystem(fhirProps.getSystems().identifiers().zenzyTherapieAutoNr())
            .setValue(record.autoNr().toString());
    medicationRequest.addIdentifier(identifier);
    medicationRequest.setId(MappingUtils.computeResourceIdFromIdentifier(identifier));

    // TODO: set the status based on the record data
    medicationRequest.setStatus(MedicationRequestStatus.ACTIVE);

    // TODO: verify if this is the correct code
    medicationRequest.setIntent(MedicationRequestIntent.ORDER);

    // TODO: we probably can't set the category (?).
    // or maybe set it to chemotherapy?
    medicationRequest.setReported(new BooleanType(false));

    var patientReference = patientReferenceGenerator.apply(record);
    medicationRequest.setSubject(patientReference);

    // TODO: authoredOn ?

    var timing = new Timing();
    var dt = record.applikationsZeitpunkt().atZone(DEFAULT_ZONE_ID).toOffsetDateTime();
    var fhirDateTime = new DateTimeType();
    fhirDateTime.setValue(Date.from(dt.toInstant()));
    fhirDateTime.setTimeZone(TimeZone.getTimeZone(dt.getOffset()));
    timing.setEvent(List.of(fhirDateTime));

    var dosage = new Dosage();
    dosage.setTiming(timing);
    var dosageRoute = new CodeableConcept();
    dosageRoute.setText(record.applikationsArt());
    dosage.setRoute(dosageRoute);

    medicationRequest.addDosageInstruction(dosage);

    CodeableConcept traegerLoesung = null;
    if (StringUtils.hasText(record.traegerLoesung())) {
      traegerLoesung = traegerLoesungMapper.map(record);
    }

    var wirkstoffMedications = wirkstoffMapper.map(record);
    var wirkstoffReferences =
        wirkstoffMedications.stream().map(MappingUtils::createReferenceToResource).toList();

    var medication = medicationMapper.map(record, wirkstoffReferences, traegerLoesung);

    medicationRequest.setMedication(MappingUtils.createReferenceToResource(medication));

    var bundle = new Bundle();
    bundle.setType(BundleType.TRANSACTION);
    bundle.setId(medicationRequest.getId());
    addBundleEntry(bundle, medicationRequest);
    addBundleEntry(bundle, medication);

    for (var wirkstoffMedication : wirkstoffMedications) {
      addBundleEntry(bundle, wirkstoffMedication);
    }

    return bundle;
  }

  private static Bundle addBundleEntry(Bundle bundle, Resource resource) {
    var resourceReference = MappingUtils.createReferenceToResource(resource);
    bundle
        .addEntry()
        .setResource(resource)
        .getRequest()
        .setMethod(HTTPVerb.PUT)
        .setUrl(resourceReference.getReference());
    return bundle;
  }
}
