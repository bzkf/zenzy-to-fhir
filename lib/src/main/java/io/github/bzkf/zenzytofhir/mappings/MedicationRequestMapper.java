package io.github.bzkf.zenzytofhir.mappings;

import io.github.bzkf.zenzytofhir.models.ZenzyTherapie;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.function.Function;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Dosage;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.MedicationRequest.MedicationRequestIntent;
import org.hl7.fhir.r4.model.MedicationRequest.MedicationRequestStatus;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Timing;
import org.hl7.fhir.r4.model.Timing.TimingRepeatComponent;
import org.hl7.fhir.r4.model.Timing.UnitsOfTime;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MedicationRequestMapper {
  private static final Logger LOG = LoggerFactory.getLogger(MedicationRequestMapper.class);
  private static final ZoneId DEFAULT_ZONE_ID = ZoneId.of("Europe/Berlin");

  private final FhirProperties fhirProps;
  private final ToCodingMapper applikationsartMapper;

  public MedicationRequestMapper(
      FhirProperties fhirProperties,
      Function<ZenzyTherapie, Reference> patientReferenceGenerator,
      ToCodingMapper applikationsartMapper) {
    this.fhirProps = fhirProperties;
    this.applikationsartMapper = applikationsartMapper;
  }

  public MedicationRequest map(
      @NonNull ZenzyTherapie therapie,
      @NonNull Reference medicationReference,
      @NonNull Reference patient) {
    // Mapping logic to convert ZenzyTherapieRecord to FHIR Bundle goes here
    var medicationRequest = new MedicationRequest();

    var identifier =
        new Identifier()
            .setSystem(fhirProps.getSystems().identifiers().zenzyTherapieAutoNr())
            .setValue(therapie.autoNr().toString());
    medicationRequest.addIdentifier(identifier);
    medicationRequest.setId(MappingUtils.computeResourceIdFromIdentifier(identifier));

    // TODO: set the status based on the record data
    // figure out what zenzy status to map to "cancelled"
    medicationRequest.setStatus(MedicationRequestStatus.ACTIVE);

    medicationRequest.setIntent(MedicationRequestIntent.ORDER);

    // TODO: we probably can't set the category (?).
    // or maybe set it to chemotherapy?
    // medicationRequest.setCategory(null)

    medicationRequest.setReported(new BooleanType(false));

    medicationRequest.setSubject(patient);

    // TODO: authoredOn ?

    var dt = therapie.applikationsZeitpunkt().atZone(DEFAULT_ZONE_ID).toOffsetDateTime();
    var fhirDateTime = new DateTimeType();
    fhirDateTime.setValue(Date.from(dt.toInstant()));
    fhirDateTime.setTimeZone(TimeZone.getTimeZone(dt.getOffset()));

    var timing = new Timing();
    timing.setEvent(List.of(fhirDateTime));

    var dosage = new Dosage();
    dosage.setTiming(timing);
    dosage.setText(therapie.applikationsart());

    var mappedApplikationsart =
        applikationsartMapper.mapApplikationsart(therapie.applikationsart());
    if (mappedApplikationsart.isPresent()) {
      var applikationsart = mappedApplikationsart.get();

      if (applikationsart.routeSnomedCode() != null) {
        var route =
            new CodeableConcept()
                .addCoding(
                    fhirProps
                        .getCodings()
                        .snomed()
                        .setCode(applikationsart.routeSnomedCode())
                        .setDisplay(applikationsart.routeSnomedDisplay()));
        dosage.setRoute(route);
      }

      if (applikationsart.methodSnomedCode() != null) {
        var method =
            new CodeableConcept()
                .addCoding(
                    fhirProps
                        .getCodings()
                        .snomed()
                        .setCode(applikationsart.methodSnomedCode())
                        .setDisplay(applikationsart.methodSnomedDisplay()));
        dosage.setMethod(method);
      }

      var repeat = new TimingRepeatComponent();
      timing.setRepeat(repeat);

      if (applikationsart.duration() != null) {
        repeat.setDuration(applikationsart.duration());
      }

      if (applikationsart.durationMax() != null) {
        repeat.setDurationMax(applikationsart.durationMax());
      }

      if (applikationsart.durationUcumUnit() != null) {
        repeat.setDurationUnit(UnitsOfTime.fromCode(applikationsart.durationUcumUnit()));
      }
    } else {
      LOG.warn("Applikationsart '{}' could not be mapped", therapie.applikationsart());
    }

    if (therapie.gesamtvolumenNumeric() != null && therapie.gesamtvolumenNumeric() > 0) {
      var quantity =
          new Quantity()
              .setCode("mL")
              .setUnit("milliliter")
              .setSystem(fhirProps.getSystems().ucum())
              .setValue(therapie.gesamtvolumenNumeric());
      dosage.addDoseAndRate().setDose(quantity);
    } else {
      LOG.warn("Gesamtvolumen <= 0");
    }

    medicationRequest.addDosageInstruction(dosage);

    medicationRequest.setMedication(medicationReference);

    return medicationRequest;
  }
}
