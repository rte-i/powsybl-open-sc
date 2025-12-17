/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.extensions;

import com.google.auto.service.AutoService;
import com.powsybl.commons.extensions.ExtensionAdderProvider;
import com.powsybl.iidm.network.Network;

@AutoService(ExtensionAdderProvider.class)
public class ShortCircuitFaultSpecExtensionAdderImplProvider implements ExtensionAdderProvider<Network, ShortCircuitFaultSpecExtension, ShortCircuitFaultSpecExtensionAdder> {

    @Override
    public String getImplementationName() {
        return "Default";
    }

    @Override
    public String getExtensionName() {
        return ShortCircuitFaultSpecExtension.NAME;
    }

    @Override
    public Class<ShortCircuitFaultSpecExtensionAdder> getAdderClass() {
        return ShortCircuitFaultSpecExtensionAdder.class;
    }

    @Override
    public ShortCircuitFaultSpecExtensionAdder newAdder(Network network) {
        return new ShortCircuitFaultSpecExtensionAdder(network);
    }
}
