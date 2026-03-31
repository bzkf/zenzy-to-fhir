package io.github.bzkf.zenzytofhir.mappings;

import io.github.bzkf.zenzytofhir.models.MedicationAndStrength;
import io.github.bzkf.zenzytofhir.models.WirkstoffDosis;
import io.github.bzkf.zenzytofhir.models.ZenzyTherapie;
import io.github.dizuker.tofhir.IdUtils;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;
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
public class WirkstoffMedicationMapper {
  private static final Logger LOG = LoggerFactory.getLogger(WirkstoffMedicationMapper.class);

  private static final String MULTI_ELEMENT_DELIMITER = "\\\\n";

  private final FhirProperties fhirProps;
  private final ToCodingMapper toCodingMapper;

  public WirkstoffMedicationMapper(FhirProperties fhirProperties, ToCodingMapper toCodingMapper) {
    this.fhirProps = fhirProperties;
    this.toCodingMapper = toCodingMapper;
  }

  public List<MedicationAndStrength> map(ZenzyTherapie therapie) {
    var result = new ArrayList<MedicationAndStrength>();

    var wirkstoffe = therapie.wirkstoff().split(MULTI_ELEMENT_DELIMITER);
    var dosen = therapie.dosis().split(MULTI_ELEMENT_DELIMITER);

    final var dosisEinheit =
        StringUtils.hasText(therapie.dosisEinheit())
            ? therapie.dosisEinheit().split(MULTI_ELEMENT_DELIMITER)
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
      medication.getMeta().addProfile(fhirProps.getProfiles().miiMedication());

      var identifier =
          new Identifier()
              .setSystem(fhirProps.getSystems().identifiers().therapieWirkstoffMedicationId())
              .setValue(MappingUtils.SLUGIFY.slugify(wirkstoffDosis.wirkstoff()));
      medication.addIdentifier(identifier);
      medication.setId(IdUtils.fromIdentifier(identifier));
      medication.setStatus(MedicationStatus.ACTIVE);

      var codeableConcept = new CodeableConcept();
      codeableConcept.setText(wirkstoffDosis.wirkstoff());

      var maybeMapped = toCodingMapper.mapWirkstoff(wirkstoffDosis.wirkstoff());
      if (maybeMapped.isPresent()) {
        var mapped = maybeMapped.get();
        var atc = fhirProps.fhir().codings().atc();
        if (mapped.atcCode() != null) {
          atc.setCode(mapped.atcCode()).setDisplay(mapped.atcDisplay());
        } else {
          // MII Medications require code to be set
          atc.getCodeElement()
              .addExtension(
                  fhirProps
                      .fhir()
                      .extensions()
                      .dataAbsentReason()
                      .setValue(new CodeType("unknown")));
        }

        codeableConcept.addCoding(atc);

        var ingredient = fhirProps.fhir().codings().snomed();
        if (mapped.snomedCode() != null) {
          ingredient.setCode(mapped.snomedCode()).setDisplay(mapped.snomedDisplay());
        } else {
          // MII Medications require ingredient to be set
          ingredient
              .getCodeElement()
              .addExtension(
                  fhirProps
                      .fhir()
                      .extensions()
                      .dataAbsentReason()
                      .setValue(new CodeType("not-applicable")));
        }

        medication.addIngredient().setItem(new CodeableConcept().addCoding(ingredient));
      } else {
        LOG.warn("Couldn't map wirkstoff {}", wirkstoffDosis.wirkstoff());
        var absentAtc = fhirProps.fhir().codings().atc();
        absentAtc
            .getCodeElement()
            .addExtension(
                fhirProps.fhir().extensions().dataAbsentReason().setValue(new CodeType("unknown")));
        codeableConcept.addCoding(absentAtc);
      }

      medication.setCode(codeableConcept);

      // TODO: check if dosis einheit is actually mg/ucum
      var amount = new Ratio();
      amount
          .getNumerator()
          .setCode(wirkstoffDosis.dosisEinheit())
          .setUnit(wirkstoffDosis.dosisEinheit())
          .setSystem(fhirProps.fhir().systems().ucum())
          .setValue(1);
      amount
          .getDenominator()
          .setCode("1")
          .setUnit("{Stueck}")
          .setSystem(fhirProps.fhir().systems().ucum())
          .setValue(1);

      medication.setAmount(amount);

      var numerator = new Quantity();
      numerator
          .setValue(wirkstoffDosis.dosis().doubleValue())
          .setCode("1")
          .setUnit("{Stueck}")
          .setSystem(fhirProps.fhir().systems().ucum());
      var strength = new Ratio();
      strength.setNumerator(numerator);

      var denominator = new Quantity();
      if (therapie.gesamtvolumenNumeric() != null && therapie.gesamtvolumenNumeric() > 0) {
        denominator
            .setValue(therapie.gesamtvolumenNumeric())
            .setUnit("milliliter")
            .setCode("mL")
            .setSystem(fhirProps.fhir().systems().ucum());
      } else {
        LOG.debug("gesamtvolumen is unset, assuming undiluted medication");
        denominator
            .setValue(1)
            .setCode("1")
            .setUnit("{Stueck}")
            .setSystem(fhirProps.fhir().systems().ucum());
      }

      strength.setDenominator(denominator);

      result.add(new MedicationAndStrength(medication, strength));
    }

    return result;
  }
}
