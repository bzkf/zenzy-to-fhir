package io.github.bzkf.zenzytofhir.mappings;

import io.github.bzkf.zenzytofhir.mappings.config.ZenzyToFhirConfig;
import io.github.dizuker.tofhir.IdUtils;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.ContactPoint.ContactPointSystem;
import org.hl7.fhir.r4.model.Device;
import org.hl7.fhir.r4.model.Device.DeviceNameType;
import org.hl7.fhir.r4.model.Device.FHIRDeviceStatus;
import org.hl7.fhir.r4.model.Identifier;
import org.springframework.stereotype.Service;

@Service
public class DeviceMapper {
  private final FhirProperties fhirProps;
  private final ZenzyToFhirConfig config;

  public DeviceMapper(FhirProperties fhirProperties, ZenzyToFhirConfig config) {
    this.fhirProps = fhirProperties;
    this.config = config;
  }

  public Device map() {
    var device = new Device();
    var identifier =
        new Identifier()
            .setSystem(fhirProps.getSystems().identifiers().deviceId())
            .setValue("zenzy-to-fhir-v" + config.version());
    device.addIdentifier(identifier);
    device.setId(IdUtils.fromIdentifier(identifier));
    device.setStatus(FHIRDeviceStatus.ACTIVE);
    device.setManufacturer("https://github.com/bzkf/");
    device.addDeviceName().setName("Zenzy-to-FHIR®").setType(DeviceNameType.USERFRIENDLYNAME);
    device.setType(
        new CodeableConcept(
            fhirProps
                .fhir()
                .codings()
                .snomed()
                .setCode("706689003")
                .setDisplay("Application program software (physical object)")));

    device.addVersion().setValue(config.version());
    device
        .addContact()
        .setSystem(ContactPointSystem.URL)
        .setValue("https://github.com/bzkf/zenzy-to-fhir/issues");

    return device;
  }
}
