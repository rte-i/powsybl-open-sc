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
 * Unit tests for ValidationResult class.
 *
 * @author GridMV Validation Team
 */
class ValidationResultTest {

    private static final double DELTA = 1e-9;

    @Test
    void passFactoryMethod() {
        ValidationResult result = ValidationResult.pass("TEST-001", 10.0, 10.5, 5.0, 10.0);

        assertTrue(result.isPassed());
        assertEquals("TEST-001", result.getTestId());
        assertEquals(10.0, result.getExpectedValue(), DELTA);
        assertEquals(10.5, result.getActualValue(), DELTA);
        assertEquals(5.0, result.getDeviationPercent(), DELTA);
        assertEquals(10.0, result.getTolerancePercent(), DELTA);
        assertNull(result.getNote());
    }

    @Test
    void failFactoryMethod() {
        ValidationResult result = ValidationResult.fail("TEST-002", 10.0, 12.0, 20.0, 10.0);

        assertFalse(result.isPassed());
        assertEquals("TEST-002", result.getTestId());
        assertEquals(10.0, result.getExpectedValue(), DELTA);
        assertEquals(12.0, result.getActualValue(), DELTA);
        assertEquals(20.0, result.getDeviationPercent(), DELTA);
        assertEquals(10.0, result.getTolerancePercent(), DELTA);
        assertNull(result.getNote());
    }

    @Test
    void failWithNoteFactoryMethod() {
        ValidationResult result = ValidationResult.failWithNote("TEST-003", 10.0, 12.0, 20.0, 10.0, "Known issue");

        assertFalse(result.isPassed());
        assertEquals("TEST-003", result.getTestId());
        assertEquals("Known issue", result.getNote());
    }

    @Test
    void testIdRequired() {
        assertThrows(NullPointerException.class,
                () -> ValidationResult.pass(null, 10.0, 10.5, 5.0, 10.0));
    }

    @Test
    void toStringPassFormat() {
        ValidationResult result = ValidationResult.pass("TEST-001", 10.0, 10.5, 5.0, 10.0);
        String str = result.toString();

        assertTrue(str.contains("PASS"));
        assertTrue(str.contains("TEST-001"));
        assertTrue(str.contains("Expected:"));
        assertTrue(str.contains("Actual:"));
        assertTrue(str.contains("Deviation:"));
        assertTrue(str.contains("kA"));
    }

    @Test
    void toStringFailFormat() {
        ValidationResult result = ValidationResult.fail("TEST-002", 10.0, 12.0, 20.0, 10.0);
        String str = result.toString();

        assertTrue(str.contains("FAIL"));
        assertTrue(str.contains("TEST-002"));
    }

    @Test
    void toStringFailWithNoteFormat() {
        ValidationResult result = ValidationResult.failWithNote("TEST-003", 10.0, 12.0, 20.0, 10.0, "Known issue");
        String str = result.toString();

        assertTrue(str.contains("FAIL"));
        assertTrue(str.contains("Note: Known issue"));
    }

    @Test
    void toStringPassDoesNotIncludeNote() {
        // Even if somehow a pass had a note, the toString doesn't show it for pass results
        ValidationResult result = ValidationResult.pass("TEST-001", 10.0, 10.5, 5.0, 10.0);
        String str = result.toString();

        assertFalse(str.contains("Note:"));
    }

    @Test
    void toStringFormattingPrecision() {
        // Test that values are formatted appropriately (4 significant figures for values, 2 decimals for percentage)
        ValidationResult result = ValidationResult.pass("TEST-001", 12.3456, 12.4567, 0.899, 1.0);
        String str = result.toString();

        // Should contain properly formatted numbers
        assertTrue(str.contains("0.90%") || str.contains("0.89%")); // deviation formatted to 2 decimal places
    }
}
