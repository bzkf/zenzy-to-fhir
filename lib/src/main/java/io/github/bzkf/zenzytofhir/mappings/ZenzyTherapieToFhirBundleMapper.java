package io.github.bzkf.zenzytofhir.mappings;

import io.github.bzkf.zenzytofhir.models.ZenzyTherapie;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleType;
import org.hl7.fhir.r4.model.Bundle.HTTPVerb;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

@Service
public class ZenzyTherapieToFhirBundleMapper {

  private static final Logger LOG = LoggerFactory.getLogger(ZenzyTherapieToFhirBundleMapper.class);
  private final FhirProperties fhirProps;
  private final HergestellteMedicationMapper medicationMapper;
  private final MedicationRequestMapper medicationRequestMapper;
  private final WirkstoffMedicationMapper wirkstoffMedicationMapper;
  private final TraegerLoesungMedicationMapper traegerLoesungMedicationMapper;

  public ZenzyTherapieToFhirBundleMapper(
      FhirProperties fhirProperties,
      HergestellteMedicationMapper medicationMapper,
      MedicationRequestMapper medicationRequestMapper,
      WirkstoffMedicationMapper wirkstoffMedicationMapper,
      TraegerLoesungMedicationMapper traegerLoesungMedicationMapper) {
    this.fhirProps = fhirProperties;
    this.medicationMapper = medicationMapper;
    this.medicationRequestMapper = medicationRequestMapper;
    this.wirkstoffMedicationMapper = wirkstoffMedicationMapper;
    this.traegerLoesungMedicationMapper = traegerLoesungMedicationMapper;
  }

  public Bundle map(ZenzyTherapie record) {
    MDC.put("autoNr", record.autoNr().toString());
    MDC.put("nr", record.nr().toString());
    MDC.put("therapieNummer", record.therapieNummer().toString());
    MDC.put("herstellungsId", record.herstellungsId());

    LOG.debug("Mapping ZenzyTherapie record to FHIR");

    var wirkstoffe = wirkstoffMedicationMapper.map(record);

    var traegerLoesung = traegerLoesungMedicationMapper.map(record);

    Reference traegerLoesungReference = null;
    if (traegerLoesung.isPresent()) {
      traegerLoesungReference = MappingUtils.createReferenceToResource(traegerLoesung.get());
      traegerLoesungReference.setDisplay(record.traegerloesung());
    } else {
      LOG.debug("No Tragerloesung specified");
    }

    var medication = medicationMapper.map(record, wirkstoffe, traegerLoesungReference);

    var medicationRequest =
        medicationRequestMapper.map(record, MappingUtils.createReferenceToResource(medication));

    var bundle = new Bundle();
    bundle.setType(BundleType.TRANSACTION);
    bundle.setId(medicationRequest.getId());
    addBundleEntry(bundle, medicationRequest);
    addBundleEntry(bundle, medication);

    for (var wirkstoff : wirkstoffe) {
      addBundleEntry(bundle, wirkstoff.medication());
    }

    if (traegerLoesung.isPresent()) {
      addBundleEntry(bundle, traegerLoesung.get());
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
