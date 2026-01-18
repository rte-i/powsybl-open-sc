/*
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.sc.validation.networks;

import com.powsybl.iidm.network.Network;
import com.powsybl.sc.util.ReferenceNetwork;

/**
 * Factory class for creating IEC 60909 reference networks for validation testing.
 *
 * <p>This class provides static factory methods that wrap existing reference networks
 * from the sc-implementation module, providing a clean validation-specific API.</p>
 *
 * @author GridMV Validation Team
 */
public final class Iec60909Networks {

    private Iec60909Networks() {
        // Utility class - prevent instantiation
    }

    /**
     * Creates the IEC 60909:2016 Section 3.1 reference network.
     *
     * <p>This network represents a low-voltage distribution network as specified
     * in IEC 60909:2016 Section 3.1, used for validating short-circuit calculations.</p>
     *
     * <h3>Network Topology:</h3>
     * <ul>
     *   <li>Bus B1: 20 kV (HV side)</li>
     *   <li>Bus B2-B6: 400 V (LV side)</li>
     *   <li>Transformers T1, T2: Dyn configuration for ground path</li>
     *   <li>Lines L1-L4: Distribution cables with Fortescue parameters</li>
     * </ul>
     *
     * <h3>Reference Values (IEC 60909:2016):</h3>
     * <ul>
     *   <li>LLL fault at B3: I"k = 34.62 kA (three-phase symmetrical)</li>
     * </ul>
     *
     * <p>Note: The expected value is calculated using IEC 60909 methodology with
     * cmax = 1.05 voltage factor for low-voltage networks.</p>
     *
     * @return a Network instance configured per IEC 60909:2016 Section 3.1
     * @see com.powsybl.sc.util.ReferenceNetwork#createShortCircuitIec31()
     */
    public static Network createSection31Network() {
        return ReferenceNetwork.createShortCircuitIec31();
    }
}
