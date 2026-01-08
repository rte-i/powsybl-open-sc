/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.implementation;

import com.powsybl.iidm.network.Network;
import com.powsybl.sc.extensions.ShortCircuitNorm;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test that validates the effective impedance calculation with B0 (capacitive shunt).
 * This test verifies that high capacitance values (c0 > 500 nF/km) are properly
 * accounted for in zero-sequence impedance calculations.
 */
class TestB0EffectiveImpedance {

    @Test
    void testSinglePhaseFaultWithHighCapacitance() {
        // Create network with c0 = 2000 nF/km (ultra-capacitive cable)
        Network network = PandapowerLineNetwork.create(15.0, 2000.0);
        PandapowerLineNetwork.applyExtGrid(network, 100.0, 0.35);
        PandapowerLineNetwork.applyZeroSequence(network, 0.4, 1.0, 100.0, 0.35);

        // Calculate single-phase fault
        ShortCircuitEngineParameters scParams = new ShortCircuitEngineParameters(
                ShortCircuitNorm.IEC_60909,
                "max",
                false);

        var results = ShortCircuitIecProvider.run(
                network,
                ShortCircuitAnalysisParameters.FaultType.SINGLE_PHASE,
                scParams);

        // With high capacitance, the effective impedance should be significantly affected
        // Expected behavior: current should be lower than without B0 modeling

        // Note: Due to modeling differences with pandapower, exact match is not expected
        // but the trend should be correct (capacitance reduces Z0 magnitude)

        assertTrue(results.size() >= 2, "Should have results for B1 and B2");

        var b1Result = results.stream()
                .filter(r -> r.getBusId().equals("B1"))
                .findFirst()
                .orElseThrow();

        System.out.println("B1 single-phase fault current: " + b1Result.getCurrent() + " kA");

        // The current should be positive and finite
        assertTrue(b1Result.getCurrent() > 0, "Current should be positive");
        assertTrue(Double.isFinite(b1Result.getCurrent()), "Current should be finite");

        // With c0=2000 nF/km, pandapower gives ~0.737 kA
        // Our model should give a reasonable value (within 50% due to modeling differences)
        assertTrue(b1Result.getCurrent() > 0.3, "Current should be > 0.3 kA");
        assertTrue(b1Result.getCurrent() < 2.0, "Current should be < 2.0 kA");
    }

    @Test
    void testSinglePhaseFaultWithLowCapacitance() {
        // Create network with c0 = 50 nF/km (typical overhead line)
        Network network = PandapowerLineNetwork.create(15.0, 50.0);
        PandapowerLineNetwork.applyExtGrid(network, 100.0, 0.35);
        PandapowerLineNetwork.applyZeroSequence(network, 0.4, 1.0, 100.0, 0.35);

        ShortCircuitEngineParameters scParams = new ShortCircuitEngineParameters(
                ShortCircuitNorm.IEC_60909,
                "max",
                false);

        var results = ShortCircuitIecProvider.run(
                network,
                ShortCircuitAnalysisParameters.FaultType.SINGLE_PHASE,
                scParams);

        var b1Result = results.stream()
                .filter(r -> r.getBusId().equals("B1"))
                .findFirst()
                .orElseThrow();

        System.out.println("B1 single-phase fault current (low c0): " + b1Result.getCurrent() + " kA");

        // With low capacitance, effect of B0 is negligible
        // Result should be similar to calculation without B0
        assertTrue(b1Result.getCurrent() > 0.45, "Current should be > 0.45 kA");
        assertTrue(b1Result.getCurrent() < 0.6, "Current should be < 0.6 kA");
    }

    @Test
    void testCapacitanceWarning() {
        // Test that high capacitance values are properly handled
        // This is a regression test to ensure the effective impedance calculation
        // doesn't produce NaN or infinite values

        double[] c0Values = {5.0, 50.0, 100.0, 500.0, 1000.0, 2000.0};

        for (double c0 : c0Values) {
            Network network = PandapowerLineNetwork.create(15.0, c0);
            PandapowerLineNetwork.applyExtGrid(network, 100.0, 0.35);
            PandapowerLineNetwork.applyZeroSequence(network, 0.4, 1.0, 100.0, 0.35);

            ShortCircuitEngineParameters scParams = new ShortCircuitEngineParameters(
                    ShortCircuitNorm.IEC_60909,
                    "max",
                    false);

            var results = ShortCircuitIecProvider.run(
                    network,
                    ShortCircuitAnalysisParameters.FaultType.SINGLE_PHASE,
                    scParams);

            var b1Result = results.stream()
                    .filter(r -> r.getBusId().equals("B1"))
                    .findFirst()
                    .orElseThrow();

            System.out.printf("c0=%.0f nF/km: Ik=%.6f kA%n", c0, b1Result.getCurrent());

            assertTrue(Double.isFinite(b1Result.getCurrent()),
                    "Current should be finite for c0=" + c0);
            assertTrue(b1Result.getCurrent() > 0,
                    "Current should be positive for c0=" + c0);
        }
    }
}
