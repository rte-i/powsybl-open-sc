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
 * Unit tests for TestCaseLoader utility class.
 *
 * @author GridMV Validation Team
 */
class TestCaseLoaderTest {

    @Test
    void loadTestCasesValid() {
        List<TestCase> testCases = TestCaseLoader.loadTestCases("reference-data/iec60909-sample.json");
        assertEquals(2, testCases.size());
        assertEquals("IEC_60909_3.1_LLL_B3", testCases.get(0).getTestId());
        assertEquals(34.62, testCases.get(0).getExpectedIkKa(), 0.001);
    }

    @Test
    void loadSingleTestCaseValid() {
        TestCase testCase = TestCaseLoader.loadSingleTestCase("reference-data/test-valid-single.json");
        assertEquals("TEST_SINGLE", testCase.getTestId());
        assertEquals("1.0", testCase.getIecSection());
        assertEquals("LLL", testCase.getFaultType());
        assertEquals("B1", testCase.getBusId());
        assertEquals(10.0, testCase.getExpectedIkKa(), 0.001);
        assertEquals(1.0, testCase.getTolerancePercent(), 0.001);
        assertEquals("Test source", testCase.getSourceReference());
        assertEquals("Test note", testCase.getNotes());
    }

    @Test
    void loadTestCasesNullPath() {
        assertThrows(NullPointerException.class, () -> TestCaseLoader.loadTestCases(null));
    }

    @Test
    void loadSingleTestCaseNullPath() {
        assertThrows(NullPointerException.class, () -> TestCaseLoader.loadSingleTestCase(null));
    }

    @Test
    void loadTestCasesMissingFile() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> TestCaseLoader.loadTestCases("reference-data/non-existent.json"));
        assertTrue(ex.getMessage().contains("Resource not found"));
    }

    @Test
    void loadSingleTestCaseMissingFile() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> TestCaseLoader.loadSingleTestCase("reference-data/non-existent.json"));
        assertTrue(ex.getMessage().contains("Resource not found"));
    }

    @Test
    void loadTestCasesMalformedJson() {
        assertThrows(UncheckedIOException.class,
                () -> TestCaseLoader.loadTestCases("reference-data/test-malformed.json"));
    }

    @Test
    void loadTestCasesMissingExpected() {
        // Should throw because 'expected' is null - Jackson wraps constructor exceptions in UncheckedIOException
        UncheckedIOException ex = assertThrows(UncheckedIOException.class,
                () -> TestCaseLoader.loadTestCases("reference-data/test-missing-expected.json"));
        // Verify the root cause is the IllegalArgumentException from TestCase constructor
        Throwable cause = ex.getCause();
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        assertInstanceOf(IllegalArgumentException.class, cause);
        assertTrue(cause.getMessage().contains("expected"));
    }
}
