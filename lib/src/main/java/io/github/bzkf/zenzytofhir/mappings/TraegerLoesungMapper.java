package io.github.bzkf.zenzytofhir.mappings;

import io.github.bzkf.zenzytofhir.models.ZenzyTherapie;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.springframework.stereotype.Service;

@Service
public class TraegerLoesungMapper {
  private final FhirProperties fhirProps;
  private final ToCodingMapper toSnomedMapper;

  public TraegerLoesungMapper(FhirProperties fhirProperties, ToCodingMapper toSnomedMapper) {
    this.fhirProps = fhirProperties;
    this.toSnomedMapper = toSnomedMapper;
  }

  public CodeableConcept map(ZenzyTherapie record) {

    var codeableConcept = new CodeableConcept();
    codeableConcept.setText(record.traegerLoesung());

    var maybeMapped = toSnomedMapper.mapTraegerloesung(record.traegerLoesung());
    if (maybeMapped.isPresent()) {
      var snomed = fhirProps.getCodings().snomed();
      var mapped = maybeMapped.get();
      if (mapped.snomedCode() != null) {
        snomed.setCode(mapped.snomedCode()).setDisplay(mapped.snomedDisplay());
        codeableConcept.addCoding(snomed);
      }
    }

    return codeableConcept;
  }
}
