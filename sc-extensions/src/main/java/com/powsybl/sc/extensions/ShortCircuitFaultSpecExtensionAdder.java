/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.extensions;

import com.powsybl.commons.extensions.AbstractExtensionAdder;
import com.powsybl.iidm.network.Network;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Adder used to populate {@link ShortCircuitFaultSpecExtension}.
 */
public class ShortCircuitFaultSpecExtensionAdder extends AbstractExtensionAdder<Network, ShortCircuitFaultSpecExtension> {

    private final Map<String, BranchFaultSpec> branchFaultSpecs = new LinkedHashMap<>();

    public ShortCircuitFaultSpecExtensionAdder(Network network) {
        super(network);
    }

    @Override
    public Class<? super ShortCircuitFaultSpecExtension> getExtensionClass() {
        return ShortCircuitFaultSpecExtension.class;
    }

    public ShortCircuitFaultSpecExtensionAdder withBranchFault(String faultId, BranchFaultSpec spec) {
        Objects.requireNonNull(faultId);
        Objects.requireNonNull(spec);
        branchFaultSpecs.put(faultId, spec);
        return this;
    }

    public ShortCircuitFaultSpecExtensionAdder withBranchFault(String faultId, String branchId, double positionAlpha) {
        return withBranchFault(faultId, branchId, positionAlpha, BranchFaultSpec.BranchSide.FROM);
    }

    public ShortCircuitFaultSpecExtensionAdder withBranchFault(String faultId, String branchId, double positionAlpha, BranchFaultSpec.BranchSide referenceSide) {
        return withBranchFault(faultId, new BranchFaultSpec(branchId, positionAlpha, referenceSide));
    }

    @Override
    protected ShortCircuitFaultSpecExtension createExtension(Network network) {
        return new ShortCircuitFaultSpecExtension(network, branchFaultSpecs);
    }
}
