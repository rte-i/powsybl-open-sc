/*
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.sc.validation.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ValidationResult}.
 *
 * @author GridMV Validation Team
 */
class ValidationResultTest {

    @Test
    void testPassFactoryMethod() {
        ValidationResult result = ValidationResult.pass("TEST_001", 10.5, 10.4, 0.95, 1.0);

        assertTrue(result.isPassed());
        assertEquals("TEST_001", result.getTestId());
        assertEquals(10.5, result.getExpectedValue());
        assertEquals(10.4, result.getActualValue());
        assertEquals(0.95, result.getDeviationPercent());
        assertEquals(1.0, result.getTolerancePercent());
        assertNull(result.getNote());
    }

    @Test
    void testFailFactoryMethod() {
        ValidationResult result = ValidationResult.fail("TEST_002", 10.5, 11.0, 4.76, 1.0);

        assertFalse(result.isPassed());
        assertEquals("TEST_002", result.getTestId());
        assertEquals(10.5, result.getExpectedValue());
        assertEquals(11.0, result.getActualValue());
        assertEquals(4.76, result.getDeviationPercent());
        assertEquals(1.0, result.getTolerancePercent());
        assertNull(result.getNote());
    }

    @Test
    void testFailWithNoteFactoryMethod() {
        ValidationResult result = ValidationResult.failWithNote("TEST_003", 10.5, 11.5, 9.52, 1.0, "Known deviation due to model simplification");

        assertFalse(result.isPassed());
        assertEquals("TEST_003", result.getTestId());
        assertEquals("Known deviation due to model simplification", result.getNote());
    }

    @Test
    void testNullTestIdThrowsException() {
        assertThrows(NullPointerException.class, () ->
            ValidationResult.pass(null, 10.0, 10.0, 0.0, 1.0));
    }

    @Test
    void testToStringFormatPass() {
        ValidationResult result = ValidationResult.pass("TEST_001", 10.5, 10.4, 0.95, 1.0);
        String output = result.toString();

        assertTrue(output.startsWith("PASS:"));
        assertTrue(output.contains("TEST_001"));
        assertTrue(output.contains("Expected:"));
        assertTrue(output.contains("Actual:"));
        assertTrue(output.contains("Deviation:"));
    }

    @Test
    void testToStringFormatFail() {
        ValidationResult result = ValidationResult.fail("TEST_002", 10.5, 11.0, 4.76, 1.0);
        String output = result.toString();

        assertTrue(output.startsWith("FAIL:"));
        assertTrue(output.contains("TEST_002"));
    }

    @Test
    void testToStringFormatFailWithNote() {
        ValidationResult result = ValidationResult.failWithNote("TEST_003", 10.5, 11.5, 9.52, 1.0, "Test note");
        String output = result.toString();

        assertTrue(output.startsWith("FAIL:"));
        assertTrue(output.contains("Note: Test note"));
    }

    @Test
    void testToStringWithEmptyNoteDoesNotIncludeNote() {
        ValidationResult result = ValidationResult.failWithNote("TEST_004", 10.0, 11.0, 10.0, 1.0, "");
        String output = result.toString();

        assertFalse(output.contains("Note:"));
    }

    @Test
    void testZeroValues() {
        ValidationResult result = ValidationResult.pass("TEST_ZERO", 0.0, 0.0, 0.0, 1.0);

        assertTrue(result.isPassed());
        assertEquals(0.0, result.getExpectedValue());
        assertEquals(0.0, result.getActualValue());
        assertEquals(0.0, result.getDeviationPercent());
    }
}
