package io.github.bzkf.zenzytofhir.mappings;

import io.github.bzkf.zenzytofhir.models.MedicationAndStrength;
import io.github.bzkf.zenzytofhir.models.ZenzyTherapie;
import java.util.List;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.Medication.MedicationStatus;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Ratio;
import org.hl7.fhir.r4.model.Reference;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class HergestellteMedicationMapper {
  private static final Logger LOG = LoggerFactory.getLogger(HergestellteMedicationMapper.class);

  private final FhirProperties fhirProps;

  public HergestellteMedicationMapper(FhirProperties fhirProperties) {
    this.fhirProps = fhirProperties;
  }

  public Medication map(
      @NonNull ZenzyTherapie therapie,
      @NonNull List<MedicationAndStrength> wirkstoffe,
      @Nullable Reference traegerLoesung) {
    var medication = new Medication();
    var identifier =
        new Identifier()
            .setSystem(fhirProps.getSystems().identifiers().zenzyHerstellungsId())
            .setValue(therapie.herstellungsId());
    medication.addIdentifier(identifier);
    medication.setId(MappingUtils.computeResourceIdFromIdentifier(identifier));
    medication.setStatus(MedicationStatus.ACTIVE);

    if (therapie.gesamtvolumenNumeric() > 0) {
      var quantity =
          new Quantity()
              .setValue(therapie.gesamtvolumenNumeric())
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
    }

    if (traegerLoesung != null) {
      var numerator =
          new Quantity()
              .setValue(therapie.gesamtvolumenNumeric())
              .setUnit("1")
              .setCode("{Stueck}")
              .setSystem(fhirProps.getSystems().ucum());
      var denominator =
          new Quantity()
              .setValue(therapie.gesamtvolumenNumeric())
              .setUnit("milliliter")
              .setCode("mL")
              .setSystem(fhirProps.getSystems().ucum());
      var strength = new Ratio().setNumerator(numerator).setDenominator(denominator);
      medication.addIngredient().setIsActive(false).setItem(traegerLoesung).setStrength(strength);
    }

    for (var wirkstoff : wirkstoffe) {
      var reference = MappingUtils.createReferenceToResource(wirkstoff.medication());
      reference.setDisplay(wirkstoff.medication().getCode().getText());
      medication
          .addIngredient()
          .setIsActive(false)
          .setItem(reference)
          .setStrength(wirkstoff.strength());
    }

    return medication;
  }
}
