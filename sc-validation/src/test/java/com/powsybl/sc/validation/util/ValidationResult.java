/*
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.sc.validation.util;

import java.util.Locale;
import java.util.Objects;

/**
 * Captures the result of a validation comparison between expected and actual short-circuit values.
 * Provides formatted output for reporting with pass/fail status, deviation percentage, and tolerance information.
 *
 * @author GridMV Validation Team
 */
public final class ValidationResult {

    private final String testId;
    private final double expectedValue;
    private final double actualValue;
    private final double tolerancePercent;
    private final boolean passed;
    private final double deviationPercent;
    private final String note;

    private ValidationResult(String testId, double expectedValue, double actualValue,
                             double tolerancePercent, boolean passed, double deviationPercent, String note) {
        this.testId = Objects.requireNonNull(testId, "testId must not be null");
        this.expectedValue = expectedValue;
        this.actualValue = actualValue;
        this.tolerancePercent = tolerancePercent;
        this.passed = passed;
        this.deviationPercent = deviationPercent;
        this.note = note;
    }

    /**
     * Creates a passing validation result.
     *
     * @param testId unique identifier for the test case
     * @param expected expected value in kA
     * @param actual actual calculated value in kA
     * @param deviationPercent calculated deviation percentage
     * @param tolerancePercent tolerance threshold that was applied
     * @return a passing ValidationResult
     */
    public static ValidationResult pass(String testId, double expected, double actual,
                                         double deviationPercent, double tolerancePercent) {
        return new ValidationResult(testId, expected, actual, tolerancePercent, true, deviationPercent, null);
    }

    /**
     * Creates a failing validation result.
     *
     * @param testId unique identifier for the test case
     * @param expected expected value in kA
     * @param actual actual calculated value in kA
     * @param deviationPercent calculated deviation percentage
     * @param tolerancePercent tolerance threshold that was exceeded
     * @return a failing ValidationResult
     */
    public static ValidationResult fail(String testId, double expected, double actual,
                                         double deviationPercent, double tolerancePercent) {
        return new ValidationResult(testId, expected, actual, tolerancePercent, false, deviationPercent, null);
    }

    /**
     * Creates a failing validation result with a note explaining the failure.
     *
     * @param testId unique identifier for the test case
     * @param expected expected value in kA
     * @param actual actual calculated value in kA
     * @param deviationPercent calculated deviation percentage
     * @param tolerancePercent tolerance threshold that was exceeded
     * @param note explanation or context for the failure
     * @return a failing ValidationResult with note
     */
    public static ValidationResult failWithNote(String testId, double expected, double actual,
                                                 double deviationPercent, double tolerancePercent, String note) {
        return new ValidationResult(testId, expected, actual, tolerancePercent, false, deviationPercent, note);
    }

    public String getTestId() {
        return testId;
    }

    public double getExpectedValue() {
        return expectedValue;
    }

    public double getActualValue() {
        return actualValue;
    }

    public double getTolerancePercent() {
        return tolerancePercent;
    }

    public boolean isPassed() {
        return passed;
    }

    public double getDeviationPercent() {
        return deviationPercent;
    }

    public String getNote() {
        return note;
    }

    /**
     * Returns a formatted string for reporting.
     * Format: PASS/FAIL: {testId} | Expected: {X} kA | Actual: {Y} kA | Deviation: {Z}%
     * For failures with notes: ... | Note: {note}
     *
     * @return formatted result string
     */
    @Override
    public String toString() {
        // Format values to 4 significant figures for precision per NFR2
        String status = passed ? "PASS" : "FAIL";
        String formatted = String.format(Locale.US, "%s: %s | Expected: %.4g kA | Actual: %.4g kA | Deviation: %.2f%%",
                status, testId, expectedValue, actualValue, deviationPercent);

        if (!passed && note != null && !note.isEmpty()) {
            formatted += " | Note: " + note;
        }

        return formatted;
    }
}
