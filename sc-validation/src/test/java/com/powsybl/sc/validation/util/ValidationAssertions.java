/*
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.sc.validation.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class providing validation assertions for short-circuit calculation results.
 * Compares actual calculated values against expected reference values with configurable tolerance.
 *
 * @author GridMV Validation Team
 */
public final class ValidationAssertions {

    private static final Logger LOGGER = LoggerFactory.getLogger(ValidationAssertions.class);

    /**
     * Small epsilon value for absolute comparison when expected value is zero.
     */
    private static final double ZERO_EPSILON = 1e-9;

    private ValidationAssertions() {
        // Utility class - prevent instantiation
    }

    /**
     * Asserts that an actual short-circuit calculation result matches the expected value within tolerance.
     * Calculates deviation as |actual - expected| / |expected| * 100.
     *
     * @param actual the actual calculated value (in kA)
     * @param expected the expected reference value (in kA)
     * @param tolerancePercent the acceptable deviation percentage (e.g., 1.0 for 1%)
     * @param testId unique identifier for the test case for traceability
     * @return ValidationResult capturing the comparison outcome
     */
    public static ValidationResult assertShortCircuitResult(double actual, double expected,
                                                            double tolerancePercent, String testId) {
        double deviationPercent = calculateDeviationPercent(actual, expected);

        ValidationResult result;
        if (deviationPercent <= tolerancePercent) {
            result = ValidationResult.pass(testId, expected, actual, deviationPercent, tolerancePercent);
        } else {
            result = ValidationResult.fail(testId, expected, actual, deviationPercent, tolerancePercent);
        }

        LOGGER.info("{}", result);
        return result;
    }

    /**
     * Asserts that an actual short-circuit calculation result matches the expected value within tolerance,
     * with an optional note for context in case of failure.
     *
     * @param actual the actual calculated value (in kA)
     * @param expected the expected reference value (in kA)
     * @param tolerancePercent the acceptable deviation percentage
     * @param testId unique identifier for the test case
     * @param note contextual note to include if validation fails
     * @return ValidationResult capturing the comparison outcome
     */
    public static ValidationResult assertShortCircuitResult(double actual, double expected,
                                                            double tolerancePercent, String testId, String note) {
        double deviationPercent = calculateDeviationPercent(actual, expected);

        ValidationResult result;
        if (deviationPercent <= tolerancePercent) {
            result = ValidationResult.pass(testId, expected, actual, deviationPercent, tolerancePercent);
        } else {
            result = ValidationResult.failWithNote(testId, expected, actual, deviationPercent, tolerancePercent, note);
        }

        LOGGER.info("{}", result);
        return result;
    }

    /**
     * Calculates the percentage deviation between actual and expected values.
     * Formula: |actual - expected| / |expected| * 100
     *
     * @param actual the actual value
     * @param expected the expected value
     * @return deviation as a percentage
     * @throws IllegalArgumentException if actual or expected is NaN or Infinite,
     *                                  or if expected is zero and actual is non-zero
     */
    static double calculateDeviationPercent(double actual, double expected) {
        // Validate inputs for NaN and Infinity
        if (Double.isNaN(actual) || Double.isNaN(expected)) {
            throw new IllegalArgumentException("NaN values are not allowed: actual=" + actual + ", expected=" + expected);
        }
        if (Double.isInfinite(actual) || Double.isInfinite(expected)) {
            throw new IllegalArgumentException("Infinite values are not allowed: actual=" + actual + ", expected=" + expected);
        }

        if (Math.abs(expected) < ZERO_EPSILON) {
            // Both zero (or very close to zero) - no deviation
            if (Math.abs(actual) < ZERO_EPSILON) {
                return 0.0;
            }
            // Expected is zero but actual is non-zero - invalid for short-circuit comparisons
            throw new IllegalArgumentException(
                    "Expected value is zero but actual is non-zero (" + actual + "). " +
                    "Zero expected values are invalid for short-circuit current comparisons.");
        }
        return Math.abs(actual - expected) / Math.abs(expected) * 100.0;
    }
}
