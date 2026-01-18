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
 * Unit tests for {@link ValidationAssertions}.
 *
 * @author GridMV Validation Team
 */
class ValidationAssertionsTest {

    @Test
    void testCalculateDeviationPercentNormalCase() {
        // 10% deviation: |11 - 10| / |10| * 100 = 10%
        double deviation = ValidationAssertions.calculateDeviationPercent(11.0, 10.0);
        assertEquals(10.0, deviation, 0.0001);
    }

    @Test
    void testCalculateDeviationPercentExactMatch() {
        double deviation = ValidationAssertions.calculateDeviationPercent(10.0, 10.0);
        assertEquals(0.0, deviation, 0.0001);
    }

    @Test
    void testCalculateDeviationPercentSmallDeviation() {
        // 0.5% deviation
        double deviation = ValidationAssertions.calculateDeviationPercent(10.05, 10.0);
        assertEquals(0.5, deviation, 0.0001);
    }

    @Test
    void testCalculateDeviationPercentBothZero() {
        double deviation = ValidationAssertions.calculateDeviationPercent(0.0, 0.0);
        assertEquals(0.0, deviation, 0.0001);
    }

    @Test
    void testCalculateDeviationPercentZeroExpectedNonZeroActualThrows() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            ValidationAssertions.calculateDeviationPercent(5.0, 0.0));
        assertTrue(ex.getMessage().contains("Expected value is zero but actual is non-zero"));
    }

    @Test
    void testCalculateDeviationPercentNaNActualThrows() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            ValidationAssertions.calculateDeviationPercent(Double.NaN, 10.0));
        assertTrue(ex.getMessage().contains("NaN values are not allowed"));
    }

    @Test
    void testCalculateDeviationPercentNaNExpectedThrows() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            ValidationAssertions.calculateDeviationPercent(10.0, Double.NaN));
        assertTrue(ex.getMessage().contains("NaN values are not allowed"));
    }

    @Test
    void testCalculateDeviationPercentPositiveInfinityActualThrows() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            ValidationAssertions.calculateDeviationPercent(Double.POSITIVE_INFINITY, 10.0));
        assertTrue(ex.getMessage().contains("Infinite values are not allowed"));
    }

    @Test
    void testCalculateDeviationPercentNegativeInfinityExpectedThrows() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            ValidationAssertions.calculateDeviationPercent(10.0, Double.NEGATIVE_INFINITY));
        assertTrue(ex.getMessage().contains("Infinite values are not allowed"));
    }

    @Test
    void testAssertShortCircuitResultPass() {
        ValidationResult result = ValidationAssertions.assertShortCircuitResult(10.05, 10.0, 1.0, "TEST_001");

        assertTrue(result.isPassed());
        assertEquals("TEST_001", result.getTestId());
        assertEquals(10.0, result.getExpectedValue());
        assertEquals(10.05, result.getActualValue());
    }

    @Test
    void testAssertShortCircuitResultFail() {
        ValidationResult result = ValidationAssertions.assertShortCircuitResult(11.5, 10.0, 1.0, "TEST_002");

        assertFalse(result.isPassed());
        assertEquals(15.0, result.getDeviationPercent(), 0.0001);
    }

    @Test
    void testAssertShortCircuitResultWithNote() {
        ValidationResult result = ValidationAssertions.assertShortCircuitResult(11.5, 10.0, 1.0, "TEST_003", "Known issue");

        assertFalse(result.isPassed());
        assertEquals("Known issue", result.getNote());
    }

    @Test
    void testAssertShortCircuitResultWithNotePass() {
        ValidationResult result = ValidationAssertions.assertShortCircuitResult(10.05, 10.0, 1.0, "TEST_004", "Should pass");

        assertTrue(result.isPassed());
        // Note should not be included when passing
        assertNull(result.getNote());
    }

    @Test
    void testAssertShortCircuitResultAtExactTolerance() {
        // Exactly at 1% tolerance should pass
        ValidationResult result = ValidationAssertions.assertShortCircuitResult(10.1, 10.0, 1.0, "TEST_005");

        assertTrue(result.isPassed());
    }

    @Test
    void testNegativeValues() {
        // Negative values should still work (absolute values used)
        double deviation = ValidationAssertions.calculateDeviationPercent(-11.0, -10.0);
        assertEquals(10.0, deviation, 0.0001);
    }

    @Test
    void testMixedSignValues() {
        // Mixed signs should work
        double deviation = ValidationAssertions.calculateDeviationPercent(-10.0, 10.0);
        assertEquals(200.0, deviation, 0.0001);
    }
}
