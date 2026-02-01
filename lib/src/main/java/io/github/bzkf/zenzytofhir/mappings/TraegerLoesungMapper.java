package io.github.bzkf.zenzytofhir.mappings;

import io.github.bzkf.zenzytofhir.models.ZenzyTherapie;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.springframework.stereotype.Service;

@Service
public class TraegerLoesungMapper {
  private final FhirProperties fhirProps;

  public TraegerLoesungMapper(FhirProperties fhirProperties) {
    this.fhirProps = fhirProperties;
  }

  public CodeableConcept map(ZenzyTherapie record) {
    // SCTID: 1268456007 - if "Glucose 5%" & Applikationsart = Infusion
    // Product containing precisely glucose 50 milligram/1 milliliter
    // conventional release solution for infusion (clinical drug)
    // 1204430002 | Product containing precisely glucose 50 milligram/1
    // milliliter conventional release solution for injection (clinical drug)

    // 1263456009 | Product containing precisely sodium chloride 9 milligram/1
    // milliliter conventional release solution for infusion (clinical drug) |

    // 782104005 | Product containing precisely sodium chloride 9 milligram/1
    // milliliter conventional release solution for injection (clinical drug)

    // 1338072000 | Product containing precisely sodium chloride 30 milligram/1
    // milliliter conventional release solution for injection (clinical drug)

    var codeableConcept = new CodeableConcept();
    codeableConcept.setText(record.traegerLoesung());

    return codeableConcept;
  }
}
