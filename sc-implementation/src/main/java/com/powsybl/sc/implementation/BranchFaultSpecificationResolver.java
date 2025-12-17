/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.implementation;

import com.powsybl.iidm.network.DanglingLine;
import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Network;
import com.powsybl.sc.extensions.BranchFaultSpec;
import com.powsybl.sc.extensions.ShortCircuitFaultSpecExtension;
import com.powsybl.shortcircuit.BranchFault;
import com.powsybl.shortcircuit.Fault;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Resolves branch fault specifications by combining network extensions and fault definitions.
 */
public class BranchFaultSpecificationResolver {

    private final ShortCircuitFaultSpecExtension faultSpecExtension;

    public BranchFaultSpecificationResolver(ShortCircuitFaultSpecExtension faultSpecExtension) {
        this.faultSpecExtension = faultSpecExtension;
    }

    public Resolution resolve(Fault fault, Network network) {
        Objects.requireNonNull(fault);
        Objects.requireNonNull(network);
        if (fault.getType() != Fault.Type.BRANCH) {
            throw new IllegalArgumentException("Fault " + fault.getId() + " is not a branch fault");
        }
        List<String> diagnostics = new ArrayList<>();

        BranchFaultSpec spec = faultSpecExtension != null
                ? faultSpecExtension.getBranchFaultSpec(fault.getId()).orElse(null)
                : null;

        String branchId;
        double positionAlpha;
        BranchFaultSpec.BranchSide referenceSide;

        if (spec != null) {
            branchId = spec.getBranchId();
            positionAlpha = spec.getPositionAlpha();
            referenceSide = spec.getReferenceSide();
        } else {
            branchId = fault.getElementId();
            if (fault instanceof BranchFault branchFault) {
                positionAlpha = branchFault.getProportionalLocation();
                diagnostics.add(String.format("Fault '%s': using branch fault proportional location %.3f", fault.getId(), positionAlpha));
            } else {
                positionAlpha = 0.5;
                diagnostics.add(String.format("Fault '%s': no branch fault spec found, defaulting location to α=0.5", fault.getId()));
            }
            referenceSide = BranchFaultSpec.BranchSide.FROM;
        }

        if (Double.isNaN(positionAlpha) || positionAlpha < 0.0 || positionAlpha > 1.0) {
            diagnostics.add(String.format("Fault '%s': branch position %.3f must be within [0, 1]", fault.getId(), positionAlpha));
            return Resolution.failure(fault.getId(), branchId, diagnostics);
        }

        Line line = network.getLine(branchId);
        if (line == null) {
            DanglingLine danglingLine = network.getDanglingLine(branchId);
            if (danglingLine != null) {
                diagnostics.add(String.format("Fault '%s': dangling line '%s' faults are not supported yet", fault.getId(), branchId));
                return Resolution.failure(fault.getId(), branchId, diagnostics);
            }
            if (network.getTwoWindingsTransformer(branchId) != null
                    || network.getThreeWindingsTransformer(branchId) != null) {
                diagnostics.add(String.format("Fault '%s': transformer '%s' faults are not supported yet", fault.getId(), branchId));
                return Resolution.failure(fault.getId(), branchId, diagnostics);
            }
            diagnostics.add(String.format("Fault '%s': branch '%s' not found", fault.getId(), branchId));
            return Resolution.failure(fault.getId(), branchId, diagnostics);
        }

        return Resolution.resolved(fault.getId(), branchId, positionAlpha, referenceSide, diagnostics);
    }

    public static final class Resolution {
        public enum Status {
            RESOLVED,
            FAILURE
        }

        private final String faultId;
        private final String branchId;
        private final double positionAlpha;
        private final BranchFaultSpec.BranchSide referenceSide;
        private final List<String> diagnostics;
        private final Status status;

        private Resolution(String faultId, String branchId, double positionAlpha,
                           BranchFaultSpec.BranchSide referenceSide, List<String> diagnostics, Status status) {
            this.faultId = faultId;
            this.branchId = branchId;
            this.positionAlpha = positionAlpha;
            this.referenceSide = referenceSide;
            this.diagnostics = diagnostics;
            this.status = status;
        }

        static Resolution resolved(String faultId, String branchId, double positionAlpha,
                                   BranchFaultSpec.BranchSide referenceSide, List<String> diagnostics) {
            return new Resolution(faultId, branchId, positionAlpha, referenceSide,
                    diagnostics == null ? List.of() : List.copyOf(diagnostics),
                    Status.RESOLVED);
        }

        static Resolution failure(String faultId, String branchId, List<String> diagnostics) {
            return new Resolution(faultId, branchId, Double.NaN, BranchFaultSpec.BranchSide.FROM,
                    diagnostics == null ? List.of() : List.copyOf(diagnostics),
                    Status.FAILURE);
        }

        public Status getStatus() {
            return status;
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

        public List<String> getDiagnostics() {
            return diagnostics;
        }

        public String buildDiagnosticsMessage(String trailingMessage) {
            List<String> parts = new ArrayList<>(diagnostics);
            if (trailingMessage != null && !trailingMessage.isEmpty()) {
                parts.add(trailingMessage);
            }
            return String.join(System.lineSeparator(), parts);
        }
    }
}
