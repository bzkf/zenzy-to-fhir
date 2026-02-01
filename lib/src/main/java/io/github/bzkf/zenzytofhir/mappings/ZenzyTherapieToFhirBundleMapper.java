package io.github.bzkf.zenzytofhir.mappings;

import static net.logstash.logback.argument.StructuredArguments.kv;

import io.github.bzkf.zenzytofhir.models.ZenzyTherapie;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleType;
import org.hl7.fhir.r4.model.Bundle.HTTPVerb;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ZenzyTherapieToFhirBundleMapper {

  private final WirkstoffMapper wirkstoffMapper;
  private static final Logger LOG = LoggerFactory.getLogger(ZenzyTherapieToFhirBundleMapper.class);
  private final FhirProperties fhirProps;
  private final HergestellteMedicationMapper medicationMapper;
  private MedicationRequestMapper medicationRequestMapper;

  public ZenzyTherapieToFhirBundleMapper(
      FhirProperties fhirProperties,
      HergestellteMedicationMapper medicationMapper,
      WirkstoffMapper wirkstoffMapper,
      MedicationRequestMapper medicationRequestMapper) {
    this.fhirProps = fhirProperties;
    this.medicationMapper = medicationMapper;
    this.wirkstoffMapper = wirkstoffMapper;
    this.medicationRequestMapper = medicationRequestMapper;
  }

  public Bundle map(ZenzyTherapie record) {
    LOG.debug("Mapping ZenzyTherapie record {} to FHIR", kv("autoNr", record.autoNr()));

    var wirkstoffMedications = wirkstoffMapper.map(record);

    var wirkstoffReferences =
        wirkstoffMedications.stream().map(MappingUtils::createReferenceToResource).toList();

    var medication = medicationMapper.map(record, wirkstoffReferences);

    var medicationRequest =
        medicationRequestMapper.map(record, MappingUtils.createReferenceToResource(medication));

    var bundle = new Bundle();
    bundle.setType(BundleType.TRANSACTION);
    bundle.setId(medicationRequest.getId());
    addBundleEntry(bundle, medicationRequest);
    addBundleEntry(bundle, medication);

    for (var wirkstoffMedication : wirkstoffMedications) {
      addBundleEntry(bundle, wirkstoffMedication);
    }

    return bundle;
  }

  private static Bundle addBundleEntry(Bundle bundle, Resource resource) {
    var resourceReference = MappingUtils.createReferenceToResource(resource);
    bundle
        .addEntry()
        .setResource(resource)
        .getRequest()
        .setMethod(HTTPVerb.PUT)
        .setUrl(resourceReference.getReference());
    return bundle;
  }
}
