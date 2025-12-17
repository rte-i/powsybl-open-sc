/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.implementation;

import com.powsybl.commons.extensions.AbstractExtension;
import com.powsybl.shortcircuit.FaultResult;

import java.util.Objects;

/**
 * Stores diagnostic information generated while processing an input fault.
 */
public class FaultProcessingDiagnostic extends AbstractExtension<FaultResult> {

    public static final String NAME = "faultProcessingDiagnostic";

    private final String message;

    public FaultProcessingDiagnostic(FaultResult faultResult, String message) {
        super(faultResult);
        this.message = Objects.requireNonNull(message);
    }

    @Override
    public String getName() {
        return NAME;
    }

    public String getMessage() {
        return message;
    }
}
