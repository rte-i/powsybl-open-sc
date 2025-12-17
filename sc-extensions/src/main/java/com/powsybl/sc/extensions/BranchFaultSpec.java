/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.extensions;

import java.util.Objects;

/**
 * Describes how a short-circuit fault is positioned on a branch.
 */
public class BranchFaultSpec {

    public enum BranchSide {
        FROM,
        TO
    }

    private final String branchId;
    private final double positionAlpha;
    private final BranchSide referenceSide;

    public BranchFaultSpec(String branchId, double positionAlpha, BranchSide referenceSide) {
        this.branchId = Objects.requireNonNull(branchId);
        if (Double.isNaN(positionAlpha)) {
            throw new IllegalArgumentException("Branch fault position cannot be NaN");
        }
        if (positionAlpha < 0.0 || positionAlpha > 1.0) {
            throw new IllegalArgumentException("Branch fault position must be within [0, 1]");
        }
        this.positionAlpha = positionAlpha;
        this.referenceSide = Objects.requireNonNull(referenceSide);
    }

    public String getBranchId() {
        return branchId;
    }

    /**
     * @return the position of the fault along the branch expressed as a
     * proportion of the total branch length.
     */
    public double getPositionAlpha() {
        return positionAlpha;
    }

    public BranchSide getReferenceSide() {
        return referenceSide;
    }
}
