/*
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.sc.validation.util;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TestCase}.
 *
 * @author GridMV Validation Team
 */
class TestCaseTest {

    private final ObjectMapper objectMapper = createObjectMapper();

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }

    @Test
    void testJsonDeserialization() throws Exception {
        String json = """
            {
              "test_id": "IEC_60909_3.1_LLL_B3",
              "iec_section": "3.1",
              "fault_type": "LLL",
              "bus_id": "B3",
              "expected": {
                "ik_ka": 34.62
              },
              "tolerance_percent": 1.0,
              "source_reference": "IEC 60909:2016 Table 3.1",
              "notes": "Three-phase fault at bus B3"
            }
            """;

        TestCase testCase = objectMapper.readValue(json, TestCase.class);

        assertEquals("IEC_60909_3.1_LLL_B3", testCase.getTestId());
        assertEquals("3.1", testCase.getIecSection());
        assertEquals("LLL", testCase.getFaultType());
        assertEquals("B3", testCase.getBusId());
        assertEquals(34.62, testCase.getExpectedIkKa());
        assertEquals(1.0, testCase.getTolerancePercent());
        assertEquals("IEC 60909:2016 Table 3.1", testCase.getSourceReference());
        assertEquals("Three-phase fault at bus B3", testCase.getNotes());
    }

    @Test
    void testJsonDeserializationMinimalFields() throws Exception {
        String json = """
            {
              "test_id": "MINIMAL_TEST",
              "expected": {
                "ik_ka": 10.0
              },
              "tolerance_percent": 1.0
            }
            """;

        TestCase testCase = objectMapper.readValue(json, TestCase.class);

        assertEquals("MINIMAL_TEST", testCase.getTestId());
        assertEquals(10.0, testCase.getExpectedIkKa());
        assertEquals(1.0, testCase.getTolerancePercent());
        assertNull(testCase.getIecSection());
        assertNull(testCase.getFaultType());
        assertNull(testCase.getBusId());
        assertNull(testCase.getSourceReference());
        assertNull(testCase.getNotes());
    }

    @Test
    void testNullTestIdThrows() {
        TestCase.ExpectedValues expected = new TestCase.ExpectedValues(10.0);
        assertThrows(NullPointerException.class, () ->
            new TestCase(null, "3.1", "LLL", "B1", expected, 1.0, "ref", "notes"));
    }

    @Test
    void testNullExpectedValues() throws Exception {
        String json = """
            {
              "test_id": "NULL_EXPECTED_TEST",
              "tolerance_percent": 1.0
            }
            """;

        TestCase testCase = objectMapper.readValue(json, TestCase.class);

        assertEquals("NULL_EXPECTED_TEST", testCase.getTestId());
        assertEquals(0.0, testCase.getExpectedIkKa());
    }

    @Test
    void testExpectedValuesDirectConstruction() {
        TestCase.ExpectedValues expected = new TestCase.ExpectedValues(25.5);
        assertEquals(25.5, expected.getIkKa());
    }

    @Test
    void testToStringContainsKeyFields() {
        TestCase.ExpectedValues expected = new TestCase.ExpectedValues(34.62);
        TestCase testCase = new TestCase("TEST_001", "3.1", "LLL", "B3", expected, 1.0, "ref", "notes");

        String output = testCase.toString();

        assertTrue(output.contains("testId='TEST_001'"));
        assertTrue(output.contains("iecSection='3.1'"));
        assertTrue(output.contains("faultType='LLL'"));
        assertTrue(output.contains("busId='B3'"));
        assertTrue(output.contains("expectedIkKa=34.62"));
        assertTrue(output.contains("tolerancePercent=1.0"));
    }

    @Test
    void testZeroExpectedValue() throws Exception {
        String json = """
            {
              "test_id": "ZERO_EXPECTED_TEST",
              "expected": {
                "ik_ka": 0.0
              },
              "tolerance_percent": 1.0
            }
            """;

        TestCase testCase = objectMapper.readValue(json, TestCase.class);
        assertEquals(0.0, testCase.getExpectedIkKa());
    }

    @Test
    void testUnknownFieldsIgnored() throws Exception {
        String json = """
            {
              "test_id": "UNKNOWN_FIELD_TEST",
              "expected": {
                "ik_ka": 10.0
              },
              "tolerance_percent": 1.0,
              "unknown_field": "should be ignored",
              "another_unknown": 123
            }
            """;

        // Should not throw - unknown fields are ignored per ObjectMapper config
        TestCase testCase = objectMapper.readValue(json, TestCase.class);
        assertEquals("UNKNOWN_FIELD_TEST", testCase.getTestId());
    }
}
