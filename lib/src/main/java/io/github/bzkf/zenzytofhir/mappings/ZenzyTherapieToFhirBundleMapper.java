package io.github.bzkf.zenzytofhir.mappings;

import io.github.bzkf.zenzytofhir.mappings.config.ZenzyToFhirConfig;
import io.github.bzkf.zenzytofhir.models.ZenzyTherapie;
import io.github.dizuker.tofhir.ReferenceUtils;
import io.github.dizuker.tofhir.TransactionBuilder;
import java.util.Optional;
import java.util.function.Function;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
  private final ZenzyToFhirConfig config;
  private final DeviceMapper deviceMapper;

  public ZenzyTherapieToFhirBundleMapper(
      HergestellteMedicationMapper medicationMapper,
      MedicationRequestMapper medicationRequestMapper,
      WirkstoffMedicationMapper wirkstoffMedicationMapper,
      TraegerLoesungMedicationMapper traegerLoesungMedicationMapper,
      DeviceMapper deviceMapper,
      Function<ZenzyTherapie, Reference> patientReferenceGenerator,
      ZenzyToFhirConfig config) {
    this.medicationMapper = medicationMapper;
    this.medicationRequestMapper = medicationRequestMapper;
    this.wirkstoffMedicationMapper = wirkstoffMedicationMapper;
    this.traegerLoesungMedicationMapper = traegerLoesungMedicationMapper;
    this.deviceMapper = deviceMapper;
    this.patientReferenceGenerator = patientReferenceGenerator;
    this.config = config;
  }

  public Optional<Bundle> map(ZenzyTherapie therapie) {
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
      traegerLoesungReference = ReferenceUtils.createReferenceTo(traegerLoesung.get());
      traegerLoesungReference.setDisplay(therapie.traegerloesung());
    } else {
      LOG.debug("No Tragerloesung specified");
    }

    var medication = medicationMapper.map(therapie, wirkstoffe, traegerLoesungReference);
    var medicationReference = ReferenceUtils.createReferenceTo(medication);

    var medicationRequest =
        medicationRequestMapper.map(therapie, medicationReference, patientReference);

    var builder =
        new TransactionBuilder()
            .withId(medicationRequest.getId())
            .addEntry(medicationRequest)
            .addEntry(medication)
            .addEntries(wirkstoffe.stream().map(w -> w.medication()).toList());

    if (traegerLoesung.isPresent()) {
      builder.addEntry(traegerLoesung.get());
    }

    if (config.mappings().provenance().enabled()) {
      var device = deviceMapper.map();
      var who = ReferenceUtils.createReferenceTo(device);
      var what = new Reference().setDisplay("Zenzy Therapie Nr " + therapie.nr());
      builder = builder.addEntry(device).withProvenance(who, what);
    }

    return Optional.of(builder.build());
  }
}
