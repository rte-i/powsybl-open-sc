/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.extensions;

import com.powsybl.commons.extensions.AbstractExtension;
import com.powsybl.iidm.network.Network;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Stores additional metadata for short-circuit faults such as branch location.
 */
public class ShortCircuitFaultSpecExtension extends AbstractExtension<Network> {

    public static final String NAME = "shortCircuitFaultSpec";

    private final Map<String, BranchFaultSpec> branchFaultSpecs;

    public ShortCircuitFaultSpecExtension(Network network, Map<String, BranchFaultSpec> branchFaultSpecs) {
        super(network);
        this.branchFaultSpecs = branchFaultSpecs == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(branchFaultSpecs);
    }

    @Override
    public String getName() {
        return NAME;
    }

    public Optional<BranchFaultSpec> getBranchFaultSpec(String faultId) {
        Objects.requireNonNull(faultId);
        return Optional.ofNullable(branchFaultSpecs.get(faultId));
    }

    public Map<String, BranchFaultSpec> getBranchFaultSpecs() {
        return branchFaultSpecs;
    }
}
