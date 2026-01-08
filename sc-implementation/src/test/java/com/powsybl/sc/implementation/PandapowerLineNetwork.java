/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.implementation;

import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Substation;
import com.powsybl.iidm.network.TopologyKind;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.extensions.GeneratorFortescueAdder;
import com.powsybl.iidm.network.extensions.GeneratorShortCircuitAdder;
import com.powsybl.iidm.network.extensions.LineFortescueAdder;
import com.powsybl.sc.extensions.GeneratorFortescueType;
import com.powsybl.sc.extensions.GeneratorFortescueTypeAdder;
import com.powsybl.sc.extensions.GeneratorShortCircuitAdder2;

final class PandapowerLineNetwork {

    static final double FREQ_HZ = 50.0;
    static final double LINE_R_OHM_PER_KM = 0.153;
    static final double LINE_X_OHM_PER_KM = 0.166;
    static final double LINE_C_NF_PER_KM = 112.0;
    static final double LINE_R0_OHM_PER_KM = 0.244;
    static final double LINE_X0_OHM_PER_KM = 0.336;
    static final double BUS_KV = 110.0;
    static final double TEMP_ALPHA = 4e-3;
    static final String LINE_PROP_LENGTH_KM = "pp.length_km";
    static final String LINE_PROP_C0_NF_PER_KM = "pp.c0_nf_per_km";

    static final String BUS_1 = "B1";
    static final String BUS_2 = "B2";
    static final String GEN_ID = "GRID";
    static final String LINE_ID = "L1";

    private PandapowerLineNetwork() {
    }

    static Network create(double lengthKm, double c0NfPerKm) {
        Network network = Network.create("pp-line", "pandapower");

        Substation substation = network.newSubstation()
                .setId("S1")
                .setCountry(Country.FR)
                .add();

        VoltageLevel vl1 = substation.newVoltageLevel()
                .setId("VL1")
                .setNominalV(BUS_KV)
                .setLowVoltageLimit(0.0)
                .setHighVoltageLimit(200.0)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        VoltageLevel vl2 = substation.newVoltageLevel()
                .setId("VL2")
                .setNominalV(BUS_KV)
                .setLowVoltageLimit(0.0)
                .setHighVoltageLimit(200.0)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();

        Bus b1 = vl1.getBusBreakerView().newBus()
                .setId(BUS_1)
                .add();
        b1.setV(BUS_KV).setAngle(0.0);

        Bus b2 = vl2.getBusBreakerView().newBus()
                .setId(BUS_2)
                .add();
        b2.setV(BUS_KV).setAngle(0.0);

        vl1.newGenerator()
                .setId(GEN_ID)
                .setBus(b1.getId())
                .setMinP(0.0)
                .setMaxP(0.0)
                .setTargetP(0.0)
                .setTargetV(BUS_KV)
                .setVoltageRegulatorOn(true)
                .setRatedS(100.0)
                .add();

        addLine(network, LINE_ID, vl1, b1, vl2, b2, lengthKm, c0NfPerKm);

        return network;
    }

    static void applyExtGrid(Network network, double sScMva, double rx) {
        Generator gen = network.getGenerator(GEN_ID);
        double z = BUS_KV * BUS_KV / sScMva;
        double x = z / Math.sqrt(1 + rx * rx);
        double r = rx * x;

        gen.newExtension(GeneratorShortCircuitAdder.class)
                .withDirectSubtransX(x)
                .withDirectTransX(x)
                .withStepUpTransformerX(0.0)
                .add();

        gen.newExtension(GeneratorShortCircuitAdder2.class)
                .withSubTransRd(r)
                .withTransRd(r)
                .add();

        gen.newExtension(GeneratorFortescueTypeAdder.class)
                .withGeneratorType(GeneratorFortescueType.GeneratorType.FEEDER)
                .add();
    }

    static void applyLineEndTemperature(Network network, double endTempDegree) {
        double factor = 1.0 + TEMP_ALPHA * (endTempDegree - 20.0);
        for (Line line : network.getLines()) {
            line.setR(line.getR() * factor);
        }
    }

    static void applyZeroSequence(Network network, double r0x0, double x0x, double sScMva, double rx) {
        Line line = network.getLine(LINE_ID);
        double length = Double.parseDouble(line.getProperty(LINE_PROP_LENGTH_KM));
        double c0NfPerKm = Double.parseDouble(line.getProperty(LINE_PROP_C0_NF_PER_KM));

        double r0Series = LINE_R0_OHM_PER_KM * length;
        double x0Series = LINE_X0_OHM_PER_KM * length;

        // Calculate effective zero-sequence impedance including capacitive shunt effect
        // Capacitance creates a shunt susceptance B0 that must be modeled
        double c0Total = c0NfPerKm * 1e-9 * length;  // Convert nF to F
        double b0 = 2.0 * Math.PI * FREQ_HZ * c0Total;  // B = 2πfC (Siemens)

        // Warning for high capacitance values
        if (c0NfPerKm > 500.0) {
            System.err.printf("WARNING: Line %s has high zero-sequence capacitance (c0=%.0f nF/km). " +
                    "B0 shunt effect is significant (>10%% impact on single-phase faults). " +
                    "Results may differ from tools that don't model B0 in parallel with series impedance.%n",
                    LINE_ID, c0NfPerKm);
        }

        // For a line with series impedance Z0_series and shunt admittance Y0_shunt = jB0,
        // the equivalent impedance is: Z0_eq = Z0_series || (1/Y0_shunt)
        // Using complex arithmetic: Z0_eq = 1 / (1/Z0_series + Y0_shunt)

        // Z0_series = r0Series + j*x0Series
        // Y0_shunt = j*b0
        // Y0_total = 1/Z0_series + j*b0
        //          = (r0Series - j*x0Series)/(r0Series^2 + x0Series^2) + j*b0

        double z0MagSq = r0Series * r0Series + x0Series * x0Series;
        double g0Series = r0Series / z0MagSq;  // Real part of 1/Z0_series
        double b0Series = -x0Series / z0MagSq; // Imag part of 1/Z0_series

        double gTotal = g0Series;
        double bTotal = b0Series + b0;  // Add shunt susceptance

        // Z0_effective = 1/Y0_total = 1/(g + jb) = (g - jb)/(g^2 + b^2)
        double yMagSq = gTotal * gTotal + bTotal * bTotal;
        double r0Effective = gTotal / yMagSq;
        double x0Effective = -bTotal / yMagSq;

        line.newExtension(LineFortescueAdder.class)
                .withRz(r0Effective)
                .withXz(x0Effective)
                .add();

        Generator gen = network.getGenerator(GEN_ID);
        double z = BUS_KV * BUS_KV / sScMva;
        double x1 = z / Math.sqrt(1 + rx * rx);
        double x0Ext = x0x * x1;
        double r0Ext = r0x0 * x0Ext;
        gen.newExtension(GeneratorFortescueAdder.class)
                .withRz(r0Ext)
                .withXz(x0Ext)
                .withGrounded(true)
                .add();
    }

    private static Line addLine(Network network, String id, VoltageLevel vl1, Bus b1, VoltageLevel vl2, Bus b2,
                                double lengthKm, double c0NfPerKm) {
        double r = LINE_R_OHM_PER_KM * lengthKm;
        double x = LINE_X_OHM_PER_KM * lengthKm;
        double cTotal = LINE_C_NF_PER_KM * 1e-9 * lengthKm;
        double b = 2.0 * Math.PI * FREQ_HZ * cTotal;
        Line line = network.newLine()
                .setId(id)
                .setVoltageLevel1(vl1.getId())
                .setBus1(b1.getId())
                .setConnectableBus1(b1.getId())
                .setVoltageLevel2(vl2.getId())
                .setBus2(b2.getId())
                .setConnectableBus2(b2.getId())
                .setR(r)
                .setX(x)
                .setG1(0.0)
                .setB1(b / 2.0)
                .setG2(0.0)
                .setB2(b / 2.0)
                .add();
        line.setProperty(LINE_PROP_LENGTH_KM, Double.toString(lengthKm));
        line.setProperty(LINE_PROP_C0_NF_PER_KM, Double.toString(c0NfPerKm));
        return line;
    }
}
