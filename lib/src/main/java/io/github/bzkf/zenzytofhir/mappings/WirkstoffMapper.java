package io.github.bzkf.zenzytofhir.mappings;

import com.github.slugify.Slugify;
import io.github.bzkf.zenzytofhir.models.ZenzyTherapie;
import java.util.ArrayList;
import java.util.List;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Medication;
import org.springframework.stereotype.Service;

@Service
public class WirkstoffMapper {
  private static final Slugify slugifier = Slugify.builder().build();
  private final FhirProperties fhirProps;

  public WirkstoffMapper(FhirProperties fhirProperties) {
    this.fhirProps = fhirProperties;
  }

  public List<Medication> map(ZenzyTherapie record) {
    var result = new ArrayList<Medication>();

    // the JSON line looks like this:
    // "WIRKSTOFF": "Fluorouracil\\nNatriumfolinat",
    // when deserialized, the string contains the literal "\n" string
    // so we escape the '\' first for regex using another '\'
    // and then also escape all '\' for Java strings...
    var wirkstoffe = record.wirkstoff().split("\\\\n");
    for (var wirkstoff : wirkstoffe) {
      var medication = new Medication();

      var identifierValue = slugifier.slugify(wirkstoff);
      var identifier =
          new Identifier()
              .setSystem(fhirProps.getSystems().identifiers().zenzyWirkstoffId())
              .setValue(identifierValue);
      medication.addIdentifier(identifier);
      medication.setId(MappingUtils.computeResourceIdFromIdentifier(identifier));

      var codeableConcept = new CodeableConcept();
      codeableConcept.setText(wirkstoff);
      medication.setCode(codeableConcept);

      result.add(medication);
    }
    return result;
  }
}
