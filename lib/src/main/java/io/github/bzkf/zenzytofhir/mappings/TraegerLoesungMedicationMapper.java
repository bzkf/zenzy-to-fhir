package io.github.bzkf.zenzytofhir.mappings;

import io.github.bzkf.zenzytofhir.models.ZenzyTherapie;
import io.github.dizuker.tofhir.IdUtils;
import java.util.Optional;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.Medication.MedicationStatus;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Ratio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class TraegerLoesungMedicationMapper {
  private static final Logger LOG = LoggerFactory.getLogger(TraegerLoesungMedicationMapper.class);

  private final FhirProperties fhirProps;
  private final ToCodingMapper toSnomedMapper;

  public TraegerLoesungMedicationMapper(
      FhirProperties fhirProperties, ToCodingMapper toSnomedMapper) {
    this.fhirProps = fhirProperties;
    this.toSnomedMapper = toSnomedMapper;
  }

  public Optional<Medication> map(ZenzyTherapie therapie) {
    if (!StringUtils.hasText(therapie.traegerloesung())) {
      return Optional.empty();
    }

    var medication = new Medication();
    medication.getMeta().addProfile(fhirProps.getProfiles().miiMedication());

    var identifier =
        new Identifier()
            .setSystem(fhirProps.getSystems().identifiers().therapieTraegerloesungMedicationId())
            .setValue(MappingUtils.SLUGIFY.slugify(therapie.traegerloesung()));
    medication.addIdentifier(identifier);
    medication.setId(IdUtils.fromIdentifier(identifier));
    medication.setStatus(MedicationStatus.ACTIVE);

    var codeableConcept = new CodeableConcept();
    codeableConcept.setText(therapie.traegerloesung());

    var maybeMapped = toSnomedMapper.mapTraegerloesung(therapie.traegerloesung());
    if (maybeMapped.isPresent()) {
      var mapped = maybeMapped.get();

      if (!StringUtils.hasText(mapped.snomedCode()) && !StringUtils.hasText(mapped.atcCode())) {
        LOG.debug("No mapping code available, likely undiluted.");
        return Optional.empty();
      }

      var snomed = fhirProps.fhir().codings().snomed();
      if (mapped.snomedCode() != null) {
        snomed.setCode(mapped.snomedCode()).setDisplay(mapped.snomedDisplay());
        codeableConcept.addCoding(snomed);
      }

      var atc = fhirProps.fhir().codings().atc();
      if (mapped.atcCode() != null) {
        atc.setCode(mapped.atcCode()).setDisplay(mapped.atcDisplay());
        codeableConcept.addCoding(atc);
      }
    } else {
      LOG.warn("Traegerlösung {} could not be mapped", therapie.traegerloesung());
    }

    medication.setCode(codeableConcept);

    var quantity =
        new Quantity()
            .setValue(1)
            .setUnit("milliliter")
            .setCode("mL")
            .setSystem(fhirProps.fhir().systems().ucum());
    var denominator =
        new Quantity()
            .setValue(1)
            .setUnit("1")
            .setCode("{Stueck}")
            .setSystem(fhirProps.fhir().systems().ucum());
    var amount = new Ratio().setNumerator(quantity).setDenominator(denominator);
    medication.setAmount(amount);

    // MII Medications require ingredient to be set, even if it's the same as the medication
    // itself
    var absentCoding = fhirProps.fhir().codings().snomed();
    absentCoding
        .getCodeElement()
        .addExtension(
            fhirProps
                .fhir()
                .extensions()
                .dataAbsentReason()
                .setValue(new CodeType("not-applicable")));
    medication.addIngredient().setItem(new CodeableConcept().addCoding(absentCoding));

    return Optional.of(medication);
  }
}
