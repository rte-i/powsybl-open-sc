/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.extensions;

import com.powsybl.commons.extensions.AbstractExtension;
import com.powsybl.iidm.network.Network;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at gmail.com>
 */
public class ShortCircuitStudyOptionsExtension extends AbstractExtension<Network> {

    public static final String NAME = "shortCircuitStudyOptions";

    public enum Norm {
        IEC_60909,
        NONE
    }

    public enum Period {
        SUB_TRANSIENT,
        TRANSIENT,
        STEADY_STATE
    }

    public enum VoltageProfile {
        NOMINAL,
        CALCULATED
    }

    private final Norm norm;
    private final Period period;
    private final VoltageProfile voltageProfile;

    public ShortCircuitStudyOptionsExtension(Network network, Norm norm, Period period, VoltageProfile voltageProfile) {
        super(network);
        this.norm = Objects.requireNonNull(norm);
        this.period = Objects.requireNonNull(period);
        this.voltageProfile = Objects.requireNonNull(voltageProfile);
    }

    @Override
    public String getName() {
        return NAME;
    }

    public Norm getNorm() {
        return norm;
    }

    public Period getPeriod() {
        return period;
    }

    public VoltageProfile getVoltageProfile() {
        return voltageProfile;
    }
}
