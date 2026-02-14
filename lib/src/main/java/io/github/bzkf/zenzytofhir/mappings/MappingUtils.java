package io.github.bzkf.zenzytofhir.mappings;

import com.github.slugify.Slugify;
import java.util.Locale;
import java.util.Objects;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.Validate;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Reference;
import org.jspecify.annotations.NonNull;

public class MappingUtils {

  public static final Slugify SLUGIFY =
      Slugify.builder().lowerCase(true).locale(Locale.GERMAN).build();

  private MappingUtils() {
    // Utility class
  }

  public static final IIdType computeResourceIdFromIdentifier(@NonNull Identifier identifier) {
    Validate.notBlank(identifier.getSystem());
    Validate.notBlank(
        identifier.getValue(),
        "Identifier value must not be blank. System: %s",
        identifier.getSystem());
    var id =
        new DigestUtils("SHA-256")
            .digestAsHex(identifier.getSystem() + "|" + identifier.getValue());
    return new IdType(id);
  }

  public static final Reference createReferenceToResource(@NonNull IBaseResource resource) {
    Objects.requireNonNull(resource.getIdElement());
    Validate.notBlank(resource.getIdElement().getIdPart());
    return new Reference()
        .setReference(resource.fhirType() + "/" + resource.getIdElement().getIdPart());
  }
}
