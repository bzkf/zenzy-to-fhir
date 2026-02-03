package io.github.bzkf.zenzytofhir.models;

import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.Ratio;

public record MedicationAndStrength(Medication medication, Ratio strength) {}
