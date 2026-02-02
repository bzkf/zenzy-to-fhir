package io.github.bzkf.zenzytofhir.mappings;

import io.github.bzkf.zenzytofhir.models.ZenzyTherapie;
import java.text.NumberFormat;
import java.text.ParseException;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class MedicationRequestMapper {
  private static final Logger LOG = LoggerFactory.getLogger(MedicationRequestMapper.class);
  private static final ZoneId DEFAULT_ZONE_ID = ZoneId.of("Europe/Berlin");

  private final FhirProperties fhirProps;
  private final Function<ZenzyTherapie, Reference> patientReferenceGenerator;
  private final ToCodingMapper applikationsartMapper;

  public MedicationRequestMapper(
      FhirProperties fhirProperties,
      Function<ZenzyTherapie, Reference> patientReferenceGenerator,
      ToCodingMapper applikationsartMapper) {
    this.fhirProps = fhirProperties;
    this.patientReferenceGenerator = patientReferenceGenerator;
    this.applikationsartMapper = applikationsartMapper;
  }

  public MedicationRequest map(ZenzyTherapie therapie, Reference medicationReference) {
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

    // TODO: verify if this is the correct code
    medicationRequest.setIntent(MedicationRequestIntent.ORDER);

    // TODO: we probably can't set the category (?).
    // medicationRequest.setCategory(null)

    // or maybe set it to chemotherapy?
    medicationRequest.setReported(new BooleanType(false));

    var patientReference = patientReferenceGenerator.apply(therapie);
    medicationRequest.setSubject(patientReference);

    // TODO: authoredOn ?

    var dt = therapie.applikationsZeitpunkt().atZone(DEFAULT_ZONE_ID).toOffsetDateTime();
    var fhirDateTime = new DateTimeType();
    fhirDateTime.setValue(Date.from(dt.toInstant()));
    fhirDateTime.setTimeZone(TimeZone.getTimeZone(dt.getOffset()));

    var timing = new Timing();
    timing.setEvent(List.of(fhirDateTime));

    var dosage = new Dosage();
    dosage.setTiming(timing);
    dosage.setText(therapie.applikationsArt());

    var a = applikationsartMapper.mapApplikationsart(therapie.applikationsArt());
    if (a.isPresent()) {
      var applikationsart = a.get();

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
    }

    if (StringUtils.hasText(therapie.dosierungsArt())) {
      var dosierungsart = therapie.dosierungsArt().split("\\\\n");
      var dosierung = therapie.dosierung().split("\\\\n");
      if (dosierung.length != dosierungsart.length) {
        throw new IllegalArgumentException(
            "Dosierung und Dosierungsart differ in length. Unable to map.");
      }

      var dosierungsartMap = new HashMap<String, String>();
      dosierungsartMap.put("KOF", "mg/m2");
      dosierungsartMap.put("GEW", "mg/kg");

      // TODO: verify how it works if one item is either KOF/GEW
      // and the other isn't. then the doseAndRate elements don't
      // map to the used medications.
      for (int i = 0; i < dosierung.length; i++) {
        var code = dosierungsartMap.get(dosierungsart[i]);
        var dosierungValue = dosierung[i];
        try {
          var dosierungNumeric = NumberFormat.getInstance(Locale.GERMAN).parse(dosierungValue);

          var quantity =
              new Quantity()
                  .setCode(code)
                  .setUnit(code)
                  .setSystem(fhirProps.getSystems().ucum())
                  .setValue(dosierungNumeric.doubleValue());
          dosage.addDoseAndRate().setDose(quantity);
        } catch (ParseException e) {
          LOG.error("Failed to parse dosierung value", e);
        }
      }
    }

    medicationRequest.addDosageInstruction(dosage);

    medicationRequest.setMedication(medicationReference);

    return medicationRequest;
  }
}
