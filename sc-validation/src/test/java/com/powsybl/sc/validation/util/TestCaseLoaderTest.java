/*
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.sc.validation.util;

import org.junit.jupiter.api.Test;

import java.io.UncheckedIOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TestCaseLoader}.
 *
 * @author GridMV Validation Team
 */
class TestCaseLoaderTest {

    @Test
    void testLoadTestCasesValidFile() {
        List<TestCase> testCases = TestCaseLoader.loadTestCases("reference-data/iec60909-sample.json");

        assertNotNull(testCases);
        assertEquals(2, testCases.size());

        TestCase first = testCases.get(0);
        assertEquals("IEC_60909_3.1_LLL_B3", first.getTestId());
        assertEquals("LLL", first.getFaultType());
        assertEquals(34.62, first.getExpectedIkKa());
    }

    @Test
    void testLoadSingleTestCase() {
        TestCase testCase = TestCaseLoader.loadSingleTestCase("reference-data/test-loader-single.json");

        assertNotNull(testCase);
        assertEquals("SINGLE_TEST", testCase.getTestId());
        assertEquals("3.1", testCase.getIecSection());
        assertEquals("LLL", testCase.getFaultType());
        assertEquals("B1", testCase.getBusId());
        assertEquals(25.0, testCase.getExpectedIkKa());
        assertEquals(1.5, testCase.getTolerancePercent());
    }

    @Test
    void testLoadTestCasesNullPathThrows() {
        assertThrows(NullPointerException.class, () ->
            TestCaseLoader.loadTestCases(null));
    }

    @Test
    void testLoadSingleTestCaseNullPathThrows() {
        assertThrows(NullPointerException.class, () ->
            TestCaseLoader.loadSingleTestCase(null));
    }

    @Test
    void testLoadTestCasesResourceNotFoundThrows() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            TestCaseLoader.loadTestCases("reference-data/nonexistent-file.json"));
        assertTrue(ex.getMessage().contains("Resource not found"));
    }

    @Test
    void testLoadSingleTestCaseResourceNotFoundThrows() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            TestCaseLoader.loadSingleTestCase("reference-data/nonexistent-file.json"));
        assertTrue(ex.getMessage().contains("Resource not found"));
    }

    @Test
    void testLoadTestCasesMalformedJsonThrows() {
        assertThrows(UncheckedIOException.class, () ->
            TestCaseLoader.loadTestCases("reference-data/test-loader-malformed.json"));
    }

    @Test
    void testLoadSingleTestCaseMalformedJsonThrows() {
        assertThrows(UncheckedIOException.class, () ->
            TestCaseLoader.loadSingleTestCase("reference-data/test-loader-malformed.json"));
    }

    @Test
    void testLoadTestCasesAllFieldsParsed() {
        List<TestCase> testCases = TestCaseLoader.loadTestCases("reference-data/iec60909-sample.json");

        // Check second test case to verify all fields are parsed correctly
        TestCase second = testCases.get(1);
        assertEquals("IEC_60909_3.2_LG_B5", second.getTestId());
        assertEquals("3.2", second.getIecSection());
        assertEquals("LG", second.getFaultType());
        assertEquals("B5", second.getBusId());
        assertEquals(12.50, second.getExpectedIkKa());
        assertEquals(1.0, second.getTolerancePercent());
        assertEquals("IEC 60909:2016 Table 3.2", second.getSourceReference());
        assertEquals("Single-phase to ground fault at bus B5", second.getNotes());
    }
}
