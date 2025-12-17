/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.implementation;

import com.powsybl.commons.extensions.AbstractExtension;
import com.powsybl.sc.extensions.ShortCircuitStudyOptionsExtension;
import com.powsybl.shortcircuit.ShortCircuitAnalysisResult;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at gmail.com>
 */
public class ShortCircuitStudyReport extends AbstractExtension<ShortCircuitAnalysisResult> {

    public static final String NAME = "shortCircuitStudyReport";

    private final ShortCircuitStudyOptionsExtension.Norm norm;
    private final ShortCircuitStudyOptionsExtension.Period period;
    private final ShortCircuitStudyOptionsExtension.VoltageProfile voltageProfile;

    public ShortCircuitStudyReport(ShortCircuitAnalysisResult shortCircuitAnalysisResult, ShortCircuitStudyOptionsExtension.Norm norm,
                                   ShortCircuitStudyOptionsExtension.Period period, ShortCircuitStudyOptionsExtension.VoltageProfile voltageProfile) {
        super(shortCircuitAnalysisResult);
        this.norm = Objects.requireNonNull(norm);
        this.period = Objects.requireNonNull(period);
        this.voltageProfile = Objects.requireNonNull(voltageProfile);
    }

    @Override
    public String getName() {
        return NAME;
    }

    public ShortCircuitStudyOptionsExtension.Norm getNorm() {
        return norm;
    }

    public ShortCircuitStudyOptionsExtension.Period getPeriod() {
        return period;
    }

    public ShortCircuitStudyOptionsExtension.VoltageProfile getVoltageProfile() {
        return voltageProfile;
    }
}
