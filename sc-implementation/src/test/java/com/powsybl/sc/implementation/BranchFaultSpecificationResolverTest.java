/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.implementation;

import com.powsybl.iidm.network.Network;
import com.powsybl.sc.extensions.ShortCircuitFaultSpecExtensionAdder;
import com.powsybl.sc.util.ReferenceNetwork;
import com.powsybl.shortcircuit.BranchFault;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BranchFaultSpecificationResolverTest {

    @Test
    void resolveFromExtension() {
        Network network = ReferenceNetwork.createShortCircuitReference();
        network.newExtension(ShortCircuitFaultSpecExtensionAdder.class)
                .withBranchFault("BF1", "B2_B3", 0.3)
                .add();

        BranchFault fault = new BranchFault("BF1", "B2_B3", 0.0);
        BranchFaultSpecificationResolver resolver = new BranchFaultSpecificationResolver(
                network.getExtension(com.powsybl.sc.extensions.ShortCircuitFaultSpecExtension.class));

        BranchFaultSpecificationResolver.Resolution resolution = resolver.resolve(fault, network);

        assertEquals(BranchFaultSpecificationResolver.Resolution.Status.RESOLVED, resolution.getStatus());
        assertEquals("B2_B3", resolution.getBranchId());
        assertEquals(0.3, resolution.getPositionAlpha(), 1e-9);
        assertTrue(resolution.getDiagnostics().isEmpty());
    }

    @Test
    void resolveWithoutSpecUsesBranchFaultDefinition() {
        Network network = ReferenceNetwork.createShortCircuitReference();
        BranchFault fault = new BranchFault("BF2", "B2_B3", 0.25);
        BranchFaultSpecificationResolver resolver = new BranchFaultSpecificationResolver(null);

        BranchFaultSpecificationResolver.Resolution resolution = resolver.resolve(fault, network);

        assertEquals(BranchFaultSpecificationResolver.Resolution.Status.RESOLVED, resolution.getStatus());
        assertEquals("B2_B3", resolution.getBranchId());
        assertEquals(0.25, resolution.getPositionAlpha(), 1e-9);
        assertFalse(resolution.getDiagnostics().isEmpty());
        assertTrue(resolution.buildDiagnosticsMessage(null).contains("proportional location"));
    }

    @Test
    void failForUnsupportedTransformerBranch() {
        Network network = ReferenceNetwork.createShortCircuitReference();
        network.newExtension(ShortCircuitFaultSpecExtensionAdder.class)
                .withBranchFault("BF3", "T1", 0.4)
                .add();

        BranchFault fault = new BranchFault("BF3", "T1", 0.4);
        BranchFaultSpecificationResolver resolver = new BranchFaultSpecificationResolver(
                network.getExtension(com.powsybl.sc.extensions.ShortCircuitFaultSpecExtension.class));

        BranchFaultSpecificationResolver.Resolution resolution = resolver.resolve(fault, network);

        assertEquals(BranchFaultSpecificationResolver.Resolution.Status.FAILURE, resolution.getStatus());
        assertTrue(resolution.buildDiagnosticsMessage(null).contains("transformer"));
    }
}
