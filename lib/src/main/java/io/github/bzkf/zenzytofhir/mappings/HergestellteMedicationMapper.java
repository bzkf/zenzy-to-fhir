package io.github.bzkf.zenzytofhir.mappings;

import io.github.bzkf.zenzytofhir.models.MedicationAndStrength;
import io.github.bzkf.zenzytofhir.models.ZenzyTherapie;
import java.util.List;
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

  public HergestellteMedicationMapper(
      FhirProperties fhirProperties,
      TraegerLoesungMapper traegerLoesungMapper,
      ToCodingMapper toCodingMapper) {
    this.fhirProps = fhirProperties;
    this.traegerLoesungMapper = traegerLoesungMapper;
    this.toCodingMapper = toCodingMapper;
  }

  public Medication map(
      @NonNull ZenzyTherapie therapie, @NonNull List<MedicationAndStrength> wirkstoffe) {
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

    for (var wirkstoff : wirkstoffe) {
      var reference = MappingUtils.createReferenceToResource(wirkstoff.medication());
      medication
          .addIngredient()
          .setIsActive(false)
          .setItem(reference)
          .setStrength(wirkstoff.strength());
    }

    return medication;
  }
}
