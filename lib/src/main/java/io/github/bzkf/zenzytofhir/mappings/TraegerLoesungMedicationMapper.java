package io.github.bzkf.zenzytofhir.mappings;

import io.github.bzkf.zenzytofhir.models.ZenzyTherapie;
import java.util.Optional;
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
    var identifier =
        new Identifier()
            .setSystem(fhirProps.getSystems().identifiers().therapieTraegerloesungMedicationId())
            .setValue(MappingUtils.SLUGIFY.slugify(therapie.traegerloesung()));
    medication.addIdentifier(identifier);
    medication.setId(MappingUtils.computeResourceIdFromIdentifier(identifier));
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

      var snomed = fhirProps.getCodings().snomed();
      if (mapped.snomedCode() != null) {
        snomed.setCode(mapped.snomedCode()).setDisplay(mapped.snomedDisplay());
        codeableConcept.addCoding(snomed);
      }

      var atc = fhirProps.getCodings().atc();
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
            .setSystem(fhirProps.getSystems().ucum());
    var denominator =
        new Quantity()
            .setValue(1)
            .setUnit("1")
            .setCode("{Stueck}")
            .setSystem(fhirProps.getSystems().ucum());
    var amount = new Ratio().setNumerator(quantity).setDenominator(denominator);
    medication.setAmount(amount);

    return Optional.of(medication);
  }
}
