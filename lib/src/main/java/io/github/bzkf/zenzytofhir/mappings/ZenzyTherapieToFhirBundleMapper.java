package io.github.bzkf.zenzytofhir.mappings;

import io.github.bzkf.zenzytofhir.models.ZenzyTherapie;
import java.util.Optional;
import java.util.function.Function;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleType;
import org.hl7.fhir.r4.model.Bundle.HTTPVerb;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ZenzyTherapieToFhirBundleMapper {

  private static final Logger LOG = LoggerFactory.getLogger(ZenzyTherapieToFhirBundleMapper.class);
  private final HergestellteMedicationMapper medicationMapper;
  private final MedicationRequestMapper medicationRequestMapper;
  private final WirkstoffMedicationMapper wirkstoffMedicationMapper;
  private final TraegerLoesungMedicationMapper traegerLoesungMedicationMapper;
  private final Function<ZenzyTherapie, Reference> patientReferenceGenerator;

  public ZenzyTherapieToFhirBundleMapper(
      HergestellteMedicationMapper medicationMapper,
      MedicationRequestMapper medicationRequestMapper,
      WirkstoffMedicationMapper wirkstoffMedicationMapper,
      TraegerLoesungMedicationMapper traegerLoesungMedicationMapper,
      Function<ZenzyTherapie, Reference> patientReferenceGenerator) {
    this.medicationMapper = medicationMapper;
    this.medicationRequestMapper = medicationRequestMapper;
    this.wirkstoffMedicationMapper = wirkstoffMedicationMapper;
    this.traegerLoesungMedicationMapper = traegerLoesungMedicationMapper;
    this.patientReferenceGenerator = patientReferenceGenerator;
  }

  public Optional<Bundle> map(ZenzyTherapie therapie) {
    MDC.put("autoNr", therapie.autoNr().toString());
    MDC.put("nr", therapie.nr().toString());
    MDC.put("therapieNummer", therapie.therapieNummer().toString());
    MDC.put("herstellungsId", therapie.herstellungsId());

    LOG.debug("Mapping ZenzyTherapie record to FHIR");

    if (!StringUtils.hasText(therapie.wirkstoff())) {
      LOG.error("Wirkstoff is unset. Unable to map.");
      return Optional.empty();
    }

    if (!StringUtils.hasText(therapie.dosis())) {
      LOG.error("Dosis is unset. Unable to map.");
      return Optional.empty();
    }

    var patientReference = patientReferenceGenerator.apply(therapie);

    var wirkstoffe = wirkstoffMedicationMapper.map(therapie);

    var traegerLoesung = traegerLoesungMedicationMapper.map(therapie);

    Reference traegerLoesungReference = null;
    if (traegerLoesung.isPresent()) {
      traegerLoesungReference = MappingUtils.createReferenceToResource(traegerLoesung.get());
      traegerLoesungReference.setDisplay(therapie.traegerloesung());
    } else {
      LOG.debug("No Tragerloesung specified");
    }

    var medication = medicationMapper.map(therapie, wirkstoffe, traegerLoesungReference);
    var medicationReference = MappingUtils.createReferenceToResource(medication);

    var medicationRequest =
        medicationRequestMapper.map(therapie, medicationReference, patientReference);

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

    return Optional.of(bundle);
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
