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
 * Unit tests for ValidationAssertions utility class.
 *
 * @author GridMV Validation Team
 */
class ValidationAssertionsTest {

    private static final double DELTA = 1e-9;

    @Test
    void calculateDeviationPercentNormalCase() {
        // 10% deviation: actual=11, expected=10
        double deviation = ValidationAssertions.calculateDeviationPercent(11.0, 10.0);
        assertEquals(10.0, deviation, DELTA);
    }

    @Test
    void calculateDeviationPercentZeroDeviation() {
        // No deviation: actual equals expected
        double deviation = ValidationAssertions.calculateDeviationPercent(10.0, 10.0);
        assertEquals(0.0, deviation, DELTA);
    }

    @Test
    void calculateDeviationPercentNegativeValues() {
        // Works with negative values: 10% deviation
        double deviation = ValidationAssertions.calculateDeviationPercent(-11.0, -10.0);
        assertEquals(10.0, deviation, DELTA);
    }

    @Test
    void calculateDeviationPercentBothZero() {
        // Both zero - deviation is 0%
        double deviation = ValidationAssertions.calculateDeviationPercent(0.0, 0.0);
        assertEquals(0.0, deviation, DELTA);
    }

    @Test
    void calculateDeviationPercentExpectedZeroActualNonZero() {
        // Expected is zero but actual is non-zero - should throw
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ValidationAssertions.calculateDeviationPercent(1.0, 0.0));
        assertTrue(ex.getMessage().contains("Expected value is zero"));
    }

    @Test
    void calculateDeviationPercentNaN() {
        // NaN actual
        assertThrows(IllegalArgumentException.class,
                () -> ValidationAssertions.calculateDeviationPercent(Double.NaN, 10.0));

        // NaN expected
        assertThrows(IllegalArgumentException.class,
                () -> ValidationAssertions.calculateDeviationPercent(10.0, Double.NaN));
    }

    @Test
    void calculateDeviationPercentInfinity() {
        // Infinite actual
        assertThrows(IllegalArgumentException.class,
                () -> ValidationAssertions.calculateDeviationPercent(Double.POSITIVE_INFINITY, 10.0));

        // Infinite expected
        assertThrows(IllegalArgumentException.class,
                () -> ValidationAssertions.calculateDeviationPercent(10.0, Double.NEGATIVE_INFINITY));
    }

    @Test
    void assertShortCircuitResultPass() {
        // 5% deviation with 10% tolerance -> pass
        ValidationResult result = ValidationAssertions.assertShortCircuitResult(
                10.5, 10.0, 10.0, "TEST-001");
        assertTrue(result.isPassed());
        assertEquals("TEST-001", result.getTestId());
        assertEquals(5.0, result.getDeviationPercent(), DELTA);
    }

    @Test
    void assertShortCircuitResultFail() {
        // 15% deviation with 10% tolerance -> fail
        ValidationResult result = ValidationAssertions.assertShortCircuitResult(
                11.5, 10.0, 10.0, "TEST-002");
        assertFalse(result.isPassed());
        assertEquals("TEST-002", result.getTestId());
        assertEquals(15.0, result.getDeviationPercent(), DELTA);
    }

    @Test
    void assertShortCircuitResultWithNote() {
        // Fail with note
        ValidationResult result = ValidationAssertions.assertShortCircuitResult(
                11.5, 10.0, 10.0, "TEST-003", "Known issue");
        assertFalse(result.isPassed());
        assertEquals("Known issue", result.getNote());
    }

    @Test
    void assertShortCircuitResultPassWithNote() {
        // Pass case ignores note
        ValidationResult result = ValidationAssertions.assertShortCircuitResult(
                10.0, 10.0, 10.0, "TEST-004", "Some note");
        assertTrue(result.isPassed());
        assertNull(result.getNote());
    }
}
