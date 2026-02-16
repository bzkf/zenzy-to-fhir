package io.github.bzkf.zenzytofhir.mappings;

import io.github.bzkf.zenzytofhir.models.MedicationAndStrength;
import io.github.bzkf.zenzytofhir.models.WirkstoffDosis;
import io.github.bzkf.zenzytofhir.models.ZenzyTherapie;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
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
import org.springframework.util.StringUtils;

@Service
public class WirkstoffMedicationMapper {
  private static final Logger LOG = LoggerFactory.getLogger(WirkstoffMedicationMapper.class);

  private final FhirProperties fhirProperties;
  private final ToCodingMapper toCodingMapper;

  public WirkstoffMedicationMapper(FhirProperties fhirProperties, ToCodingMapper toCodingMapper) {
    this.fhirProperties = fhirProperties;
    this.toCodingMapper = toCodingMapper;
  }

  public List<MedicationAndStrength> map(ZenzyTherapie therapie) {
    var result = new ArrayList<MedicationAndStrength>();

    var wirkstoffe = therapie.wirkstoff().split("\\\\n");
    var dosen = therapie.dosis().split("\\\\n");

    final var dosisEinheit =
        StringUtils.hasText(therapie.dosisEinheit())
            ? therapie.dosisEinheit().split("\\\\n")
            : new String[wirkstoffe.length];

    if (!StringUtils.hasText(therapie.dosisEinheit())) {
      LOG.debug("Dosis einheit is not set, defaulting to 'mg'");
      Arrays.fill(dosisEinheit, "mg");
    }

    if (wirkstoffe.length != dosen.length) {
      throw new IllegalArgumentException("substance and dosis length mismatch");
    }

    if (dosisEinheit != null && wirkstoffe.length != dosisEinheit.length) {
      throw new IllegalArgumentException("substance and dosis unit length mismatch");
    }

    var zipped =
        IntStream.range(0, wirkstoffe.length)
            .mapToObj(
                i -> {
                  try {
                    var einheit = dosisEinheit != null ? dosisEinheit[i] : "mg";
                    return new WirkstoffDosis(
                        wirkstoffe[i],
                        NumberFormat.getInstance(Locale.GERMAN).parse(dosen[i]),
                        einheit);
                  } catch (ParseException e) {
                    throw new IllegalArgumentException("Failed to parse dosis", e);
                  }
                })
            .toList();

    for (var wirkstoffDosis : zipped) {
      var medication = new Medication();
      var identifier =
          new Identifier()
              .setSystem(fhirProperties.getSystems().identifiers().therapieWirkstoffMedicationId())
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

      // TODO: check if dosis einheit is actually mg/ucum
      var amount = new Ratio();
      amount
          .getNumerator()
          .setCode(wirkstoffDosis.dosisEinheit())
          .setUnit(wirkstoffDosis.dosisEinheit())
          .setSystem(fhirProperties.getSystems().ucum())
          .setValue(1);
      amount
          .getDenominator()
          .setCode("1")
          .setUnit("{Stueck}")
          .setSystem(fhirProperties.getSystems().ucum())
          .setValue(1);

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
