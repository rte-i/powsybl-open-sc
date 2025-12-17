/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.implementation;

import com.powsybl.sc.extensions.BranchFaultSpec;

import java.util.Objects;

/**
 * Stores the resolution of a branch fault configuration.
 */
public class BranchFaultContext {

    private final String faultId;
    private final String branchId;
    private final double positionAlpha;
    private final BranchFaultSpec.BranchSide referenceSide;

    public BranchFaultContext(String faultId, String branchId, double positionAlpha, BranchFaultSpec.BranchSide referenceSide) {
        this.faultId = Objects.requireNonNull(faultId);
        this.branchId = Objects.requireNonNull(branchId);
        this.referenceSide = Objects.requireNonNull(referenceSide);
        this.positionAlpha = positionAlpha;
    }

    public String getFaultId() {
        return faultId;
    }

    public String getBranchId() {
        return branchId;
    }

    public double getPositionAlpha() {
        return positionAlpha;
    }

    public BranchFaultSpec.BranchSide getReferenceSide() {
        return referenceSide;
    }
}
