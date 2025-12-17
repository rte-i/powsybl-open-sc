/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.extensions;

import com.powsybl.commons.extensions.AbstractExtensionAdder;
import com.powsybl.iidm.network.Network;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at gmail.com>
 */
public class ShortCircuitStudyOptionsExtensionAdder extends AbstractExtensionAdder<Network, ShortCircuitStudyOptionsExtension> {

    private ShortCircuitStudyOptionsExtension.Norm norm = ShortCircuitStudyOptionsExtension.Norm.IEC_60909;
    private ShortCircuitStudyOptionsExtension.Period period = ShortCircuitStudyOptionsExtension.Period.SUB_TRANSIENT;
    private ShortCircuitStudyOptionsExtension.VoltageProfile voltageProfile = ShortCircuitStudyOptionsExtension.VoltageProfile.NOMINAL;

    public ShortCircuitStudyOptionsExtensionAdder(Network network) {
        super(network);
    }

    @Override
    public Class<? super ShortCircuitStudyOptionsExtension> getExtensionClass() {
        return ShortCircuitStudyOptionsExtension.class;
    }

    public ShortCircuitStudyOptionsExtensionAdder withNorm(ShortCircuitStudyOptionsExtension.Norm norm) {
        this.norm = Objects.requireNonNull(norm);
        return this;
    }

    public ShortCircuitStudyOptionsExtensionAdder withPeriod(ShortCircuitStudyOptionsExtension.Period period) {
        this.period = Objects.requireNonNull(period);
        return this;
    }

    public ShortCircuitStudyOptionsExtensionAdder withVoltageProfile(ShortCircuitStudyOptionsExtension.VoltageProfile voltageProfile) {
        this.voltageProfile = Objects.requireNonNull(voltageProfile);
        return this;
    }

    @Override
    protected ShortCircuitStudyOptionsExtension createExtension(Network network) {
        return new ShortCircuitStudyOptionsExtension(network, norm, period, voltageProfile);
    }
}
