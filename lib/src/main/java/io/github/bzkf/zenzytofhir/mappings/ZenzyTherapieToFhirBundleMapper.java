package io.github.bzkf.zenzytofhir.mappings;

import io.github.bzkf.zenzytofhir.models.ZenzyTherapie;
import java.util.function.Function;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.Validate;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Dosage;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.MedicationRequest.MedicationRequestIntent;
import org.hl7.fhir.r4.model.MedicationRequest.MedicationRequestStatus;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Timing;
import org.springframework.stereotype.Service;

@Service
public class ZenzyTherapieToFhirBundleMapper {
  private final FhirProperties fhirProps;
  private final Function<ZenzyTherapie, Reference> patientReferenceGenerator;

  public ZenzyTherapieToFhirBundleMapper(
      FhirProperties fhirProperties, Function<ZenzyTherapie, Reference> patientReferenceGenerator) {
    this.fhirProps = fhirProperties;
    this.patientReferenceGenerator = patientReferenceGenerator;
  }

  public Bundle map(ZenzyTherapie record) {

    // Mapping logic to convert ZenzyTherapieRecord to FHIR Bundle goes here
    var medicationRequest = new MedicationRequest();

    var identifier =
        new Identifier()
            .setSystem(fhirProps.getSystems().identifiers().zenzyTherapieNr())
            .setValue(record.nr().toString());
    medicationRequest.addIdentifier(identifier);
    medicationRequest.setId(computeResourceIdFromIdentifier(identifier));

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
    timing.addEvent(record.applikationsZeitpunkt());

    var dosage = new Dosage();
    dosage.setTiming(timing);

    var dosageRoute = new CodeableConcept();
    dosageRoute.setText(record.applikationsArt());
    dosage.setRoute(dosageRoute);

    medicationRequest.addDosageInstruction(dosage);

    var medication = new CodeableConcept();
    medication.setText(record.wirkstoff());
    medicationRequest.setMedication(medication);

    var bundle = new Bundle();
    bundle.setType(BundleType.TRANSACTION);
    bundle.setId(medicationRequest.getId());
    bundle.addEntry().setResource(medicationRequest);

    return bundle;
  }

  private static final IIdType computeResourceIdFromIdentifier(Identifier identifier) {
    Validate.notBlank(identifier.getSystem());
    Validate.notBlank(
        identifier.getValue(),
        "Identifier value must not be blank. System: %s",
        identifier.getSystem());
    var id =
        new DigestUtils("SHA-256")
            .digestAsHex(identifier.getSystem() + "|" + identifier.getValue());
    return new IdType(id);
  }
}
