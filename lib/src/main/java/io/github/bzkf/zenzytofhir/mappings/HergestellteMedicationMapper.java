package io.github.bzkf.zenzytofhir.mappings;

import io.github.bzkf.zenzytofhir.models.ZenzyTherapie;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Locale;
import java.util.stream.IntStream;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.Medication.MedicationStatus;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Ratio;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class HergestellteMedicationMapper {
  private static final Logger LOG = LoggerFactory.getLogger(HergestellteMedicationMapper.class);

  private final FhirProperties fhirProps;
  private final TraegerLoesungMapper traegerLoesungMapper;
  private final ToCodingMapper toCodingMapper;

  private record WirkstoffDosis(String wirkstoff, Number dosis, String dosisEinheit) {}

  public HergestellteMedicationMapper(
      FhirProperties fhirProperties,
      TraegerLoesungMapper traegerLoesungMapper,
      ToCodingMapper toCodingMapper) {
    this.fhirProps = fhirProperties;
    this.traegerLoesungMapper = traegerLoesungMapper;
    this.toCodingMapper = toCodingMapper;
  }

  public Medication map(@NonNull ZenzyTherapie therapie) {
    var medication = new Medication();
    var identifier =
        new Identifier()
            .setSystem(fhirProps.getSystems().identifiers().zenzyHerstellungsId())
            .setValue(therapie.herstellungsId());
    medication.addIdentifier(identifier);
    medication.setId(MappingUtils.computeResourceIdFromIdentifier(identifier));
    medication.setStatus(MedicationStatus.ACTIVE);

    if (therapie.gesamtvolumenNumeric() > 0) {
      var quantity = new Quantity();
      quantity.setValue(therapie.gesamtvolumenNumeric());
      quantity.setUnit("milliliter");
      quantity.setCode("mL");
      quantity.setSystem(fhirProps.getSystems().ucum());

      var amount = new Ratio();
      amount.setNumerator(quantity);
      amount.setDenominator(new Quantity(1));
      medication.setAmount(amount);
    }

    if (StringUtils.hasText(therapie.traegerLoesung())) {
      var traegerLoesung = traegerLoesungMapper.map(therapie);
      medication.addIngredient().setIsActive(false).setItem(traegerLoesung);
    }

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
      var codeableConcept = new CodeableConcept();
      codeableConcept.setText(wirkstoffDosis.wirkstoff());

      var maybeMapped = toCodingMapper.mapWirkstoff(wirkstoffDosis.wirkstoff());
      if (maybeMapped.isPresent()) {
        var atc = fhirProps.getCodings().atc();
        var mapped = maybeMapped.get();
        if (mapped.atcCode() != null) {
          atc.setCode(mapped.atcCode()).setDisplay(mapped.atcDisplay());
          codeableConcept.addCoding(atc);
        }
      }

      var numerator = new Quantity();
      numerator.setValue(wirkstoffDosis.dosis().doubleValue());
      numerator.setSystem(fhirProps.getSystems().ucum());

      if (wirkstoffDosis.dosisEinheit() != null) {
        numerator.setCode(wirkstoffDosis.dosisEinheit());
      } else {
        LOG.debug("Dosis unit is unset, defaulting to mg");
        numerator.setCode("mg");
        numerator.setUnit("mg");
      }

      var strength = new Ratio();
      strength.setNumerator(numerator);
      strength.setDenominator(new Quantity(1));

      medication.addIngredient().setIsActive(true).setItem(codeableConcept).setStrength(strength);
    }

    return medication;
  }
}
