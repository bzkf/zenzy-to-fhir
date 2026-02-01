package io.github.bzkf.zenzytofhir.mappings;

import io.github.bzkf.zenzytofhir.models.ZenzyTherapie;
import java.util.List;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.Medication.MedicationStatus;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Ratio;
import org.hl7.fhir.r4.model.Reference;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class HergestellteMedicationMapper {
  private final FhirProperties fhirProps;
  private final TraegerLoesungMapper traegerLoesungMapper;

  public HergestellteMedicationMapper(
      FhirProperties fhirProperties, TraegerLoesungMapper traegerLoesungMapper) {
    this.fhirProps = fhirProperties;
    this.traegerLoesungMapper = traegerLoesungMapper;
  }

  public Medication map(
      @NonNull ZenzyTherapie therapie, @NonNull List<Reference> wirkstoffMedicationReferences) {
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

    for (var wirkstoffMedication : wirkstoffMedicationReferences) {
      medication.addIngredient().setIsActive(true).setItem(wirkstoffMedication);
    }

    return medication;
  }
}
