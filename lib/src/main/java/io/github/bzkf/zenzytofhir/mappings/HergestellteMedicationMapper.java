package io.github.bzkf.zenzytofhir.mappings;

import io.github.bzkf.zenzytofhir.models.ZenzyTherapie;
import java.util.List;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.Medication.MedicationStatus;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Ratio;
import org.hl7.fhir.r4.model.Reference;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

@Service
public class HergestellteMedicationMapper {
  private final FhirProperties fhirProps;

  public HergestellteMedicationMapper(FhirProperties fhirProperties) {
    this.fhirProps = fhirProperties;
  }

  public Medication map(
      @NonNull ZenzyTherapie therapie,
      @NonNull List<Reference> wirkstoffMedicationReferences,
      @Nullable CodeableConcept traegerLoesung) {
    var medication = new Medication();
    var identifier =
        new Identifier()
            .setSystem(fhirProps.getSystems().identifiers().zenzyHerstellungsId())
            .setValue(therapie.herstellungsId().toString());
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

    if (traegerLoesung != null) {
      medication.addIngredient().setIsActive(false).setItem(traegerLoesung);
    }

    for (var wirkstoffMedication : wirkstoffMedicationReferences) {
      medication.addIngredient().setIsActive(true).setItem(wirkstoffMedication);
    }

    return medication;
  }
}
