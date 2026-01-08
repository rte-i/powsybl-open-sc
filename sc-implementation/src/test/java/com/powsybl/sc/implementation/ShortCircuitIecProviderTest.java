/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.implementation;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.computation.ComputationManager;
import com.powsybl.computation.local.LocalComputationManager;
import com.powsybl.iidm.network.Network;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.sc.extensions.ShortCircuitStudyOptionsExtension;
import com.powsybl.sc.extensions.ShortCircuitStudyOptionsExtensionAdder;
import com.powsybl.sc.util.ReferenceNetwork;
import com.powsybl.shortcircuit.BusFault;
import com.powsybl.shortcircuit.Fault;
import com.powsybl.shortcircuit.ShortCircuitAnalysisProvider;
import com.powsybl.shortcircuit.ShortCircuitAnalysisResult;
import com.powsybl.shortcircuit.ShortCircuitParameters;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at gmail.com>
 */
class ShortCircuitIecProviderTest {

    @Test
    void usesIecByDefault() {
        Network network = ReferenceNetwork.createShortCircuitReference();
        ShortCircuitAnalysisProvider provider = new OpenShortCircuitProvider(new DenseMatrixFactory());
        ComputationManager cm = LocalComputationManager.getDefault();
        ShortCircuitParameters scp = new ShortCircuitParameters();

        List<Fault> faults = Collections.singletonList(new BusFault("F1", "B1"));

        ShortCircuitAnalysisResult scar = provider.run(network, faults, scp, cm, Collections.emptyList(), ReportNode.NO_OP).join();

        ShortCircuitStudyReport report = scar.getExtension(ShortCircuitStudyReport.class);
        assertNotNull(report);
        assertEquals(ShortCircuitStudyOptionsExtension.Norm.IEC_60909, report.getNorm());
        assertEquals(ShortCircuitStudyOptionsExtension.Period.SUB_TRANSIENT, report.getPeriod());
        assertEquals(ShortCircuitStudyOptionsExtension.VoltageProfile.NOMINAL, report.getVoltageProfile());
        assertTrue(report.getDiagnostics().isEmpty());
    }

    @Test
    void usesNetworkStudyOptionsExtension() {
        Network network = ReferenceNetwork.createShortCircuitReference();
        network.newExtension(ShortCircuitStudyOptionsExtensionAdder.class)
                .withNorm(ShortCircuitStudyOptionsExtension.Norm.NONE)
                .withPeriod(ShortCircuitStudyOptionsExtension.Period.TRANSIENT)
                .withVoltageProfile(ShortCircuitStudyOptionsExtension.VoltageProfile.CALCULATED)
                .add();
        ShortCircuitAnalysisProvider provider = new OpenShortCircuitProvider(new DenseMatrixFactory());
        ComputationManager cm = LocalComputationManager.getDefault();
        ShortCircuitParameters scp = new ShortCircuitParameters();

        List<Fault> faults = Collections.singletonList(new BusFault("F1", "B1"));

        ShortCircuitAnalysisResult scar = provider.run(network, faults, scp, cm, Collections.emptyList(), ReportNode.NO_OP).join();

        ShortCircuitStudyReport report = scar.getExtension(ShortCircuitStudyReport.class);
        assertNotNull(report);
        assertEquals(ShortCircuitStudyOptionsExtension.Norm.NONE, report.getNorm());
        assertEquals(ShortCircuitStudyOptionsExtension.Period.TRANSIENT, report.getPeriod());
        assertEquals(ShortCircuitStudyOptionsExtension.VoltageProfile.CALCULATED, report.getVoltageProfile());
        assertTrue(report.getDiagnostics().isEmpty());
    }
}
