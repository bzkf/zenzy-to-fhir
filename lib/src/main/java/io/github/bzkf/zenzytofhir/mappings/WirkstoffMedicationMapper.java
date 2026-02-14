package io.github.bzkf.zenzytofhir.mappings;

import io.github.bzkf.zenzytofhir.models.MedicationAndStrength;
import io.github.bzkf.zenzytofhir.models.WirkstoffDosis;
import io.github.bzkf.zenzytofhir.models.ZenzyTherapie;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.Medication.MedicationStatus;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Ratio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class WirkstoffMedicationMapper {
  private static final Logger LOG = LoggerFactory.getLogger(WirkstoffMedicationMapper.class);

  private final FhirProperties fhirProperties;
  private final ToCodingMapper toCodingMapper;
  private final Ratio singlePieceStrength;

  public WirkstoffMedicationMapper(FhirProperties fhirProperties, ToCodingMapper toCodingMapper) {
    this.fhirProperties = fhirProperties;
    this.toCodingMapper = toCodingMapper;

    singlePieceStrength = new Ratio();
    singlePieceStrength
        .getNumerator()
        .setCode("mg")
        .setUnit("mg")
        .setSystem(fhirProperties.getSystems().ucum())
        .setValue(1);
    singlePieceStrength
        .getDenominator()
        .setCode("1")
        .setUnit("{Stueck}")
        .setSystem(fhirProperties.getSystems().ucum())
        .setValue(1);
  }

  public List<MedicationAndStrength> map(ZenzyTherapie therapie) {
    var result = new ArrayList<MedicationAndStrength>();

    var wirkstoffe = therapie.wirkstoff().split("\\\\n");
    var dosen = therapie.dosis().split("\\\\n");
    // var dosisEinheit = therapie.dosisEinheit().split("\\\\n");

    if (wirkstoffe.length != dosen.length) {
      throw new IllegalArgumentException("substance and dosis length mismatch");
    }

    var zipped =
        IntStream.range(0, wirkstoffe.length)
            .mapToObj(
                i -> {
                  try {
                    // TODO: dosis einheit
                    return new WirkstoffDosis(
                        wirkstoffe[i],
                        NumberFormat.getInstance(Locale.GERMAN).parse(dosen[i]),
                        null);
                  } catch (ParseException e) {
                    throw new IllegalArgumentException("Failed to parse dosis", e);
                  }
                })
            .toList();

    for (var wirkstoffDosis : zipped) {
      var medication = new Medication();
      var identifier =
          new Identifier()
              .setSystem(fhirProperties.getSystems().identifiers().zenzyWirkstoffId())
              .setValue(MappingUtils.SLUGIFY.slugify(wirkstoffDosis.wirkstoff()));
      medication.addIdentifier(identifier);
      medication.setId(MappingUtils.computeResourceIdFromIdentifier(identifier));
      medication.setStatus(MedicationStatus.ACTIVE);

      var codeableConcept = new CodeableConcept();
      codeableConcept.setText(wirkstoffDosis.wirkstoff());

      var maybeMapped = toCodingMapper.mapWirkstoff(wirkstoffDosis.wirkstoff());
      if (maybeMapped.isPresent()) {
        var mapped = maybeMapped.get();
        if (mapped.atcCode() != null) {
          var atc = fhirProperties.getCodings().atc();
          atc.setCode(mapped.atcCode()).setDisplay(mapped.atcDisplay());
          codeableConcept.addCoding(atc);
        }

        if (mapped.snomedCode() != null) {
          var snomed = fhirProperties.getCodings().snomed();
          snomed.setCode(mapped.snomedCode()).setDisplay(mapped.snomedDisplay());
          codeableConcept.addCoding(snomed);
        }
      } else {
        // TODO: data absent extension
        LOG.warn("Couldn't map wirkstoff {}", wirkstoffDosis.wirkstoff());
      }

      medication.setCode(codeableConcept);

      // copy() since we are mutating the value if a dosisEinheit is set
      var amount = singlePieceStrength.copy();
      // TODO: check if the dosis einheit is actually UCUM
      // the default is mg
      if (wirkstoffDosis.dosisEinheit() != null) {
        amount.getNumerator().setCode(wirkstoffDosis.dosisEinheit());
        amount.getNumerator().setUnit(wirkstoffDosis.dosisEinheit());
      } else {
        LOG.debug("Dosis einheit unset, assuming mg");
      }

      medication.setAmount(amount);

      var numerator = new Quantity();
      numerator
          .setValue(wirkstoffDosis.dosis().doubleValue())
          .setCode("1")
          .setUnit("{Stueck}")
          .setSystem(fhirProperties.getSystems().ucum());
      var strength = new Ratio();
      strength.setNumerator(numerator);

      if (therapie.gesamtvolumenNumeric() != null && therapie.gesamtvolumenNumeric() > 0) {
        var denominator =
            new Quantity()
                .setValue(therapie.gesamtvolumenNumeric())
                .setUnit("milliliter")
                .setCode("mL")
                .setSystem(fhirProperties.getSystems().ucum());
        strength.setDenominator(denominator);
      }

      result.add(new MedicationAndStrength(medication, strength));
    }

    return result;
  }
}
