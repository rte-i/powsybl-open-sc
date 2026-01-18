/*
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.sc.validation.util;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Strongly-typed representation of a validation test case loaded from JSON reference data.
 * Maps to the JSON schema defined in the architecture document with snake_case field naming.
 *
 * @author GridMV Validation Team
 */
public class TestCase {

    private final String testId;
    private final String iecSection;
    private final String faultType;
    private final String busId;
    private final double expectedIkKa;
    private final double tolerancePercent;
    private final String sourceReference;
    private final String notes;

    @JsonCreator
    public TestCase(
            @JsonProperty("test_id") String testId,
            @JsonProperty("iec_section") String iecSection,
            @JsonProperty("fault_type") String faultType,
            @JsonProperty("bus_id") String busId,
            @JsonProperty("expected") ExpectedValues expected,
            @JsonProperty("tolerance_percent") double tolerancePercent,
            @JsonProperty("source_reference") String sourceReference,
            @JsonProperty("notes") String notes) {
        this.testId = Objects.requireNonNull(testId, "test_id must not be null");
        this.iecSection = iecSection;
        this.faultType = faultType;
        this.busId = busId;
        this.expectedIkKa = expected != null ? expected.getIkKa() : 0.0;
        this.tolerancePercent = tolerancePercent;
        this.sourceReference = sourceReference;
        this.notes = notes;
    }

    /**
     * Nested class for the "expected" object in JSON.
     */
    public static class ExpectedValues {
        private final double ikKa;

        @JsonCreator
        public ExpectedValues(@JsonProperty("ik_ka") double ikKa) {
            this.ikKa = ikKa;
        }

        public double getIkKa() {
            return ikKa;
        }
    }

    public String getTestId() {
        return testId;
    }

    public String getIecSection() {
        return iecSection;
    }

    public String getFaultType() {
        return faultType;
    }

    public String getBusId() {
        return busId;
    }

    public double getExpectedIkKa() {
        return expectedIkKa;
    }

    public double getTolerancePercent() {
        return tolerancePercent;
    }

    public String getSourceReference() {
        return sourceReference;
    }

    public String getNotes() {
        return notes;
    }

    @Override
    public String toString() {
        return "TestCase{" +
                "testId='" + testId + '\'' +
                ", iecSection='" + iecSection + '\'' +
                ", faultType='" + faultType + '\'' +
                ", busId='" + busId + '\'' +
                ", expectedIkKa=" + expectedIkKa +
                ", tolerancePercent=" + tolerancePercent +
                '}';
    }
}
