/*
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.sc.validation.section31;

import com.powsybl.sc.implementation.ShortCircuitFault;
import org.junit.jupiter.api.Test;

/**
 * IEC 60909:2016 Section 3.1 LL (Phase-to-Phase) Fault Validation Tests.
 *
 * <p>Validates phase-to-phase short-circuit calculations against
 * IEC 60909 expected values using the ShortCircuitUnbalancedEngine.</p>
 *
 * <p>LL faults use ShortCircuitFault.ShortCircuitType.BIPHASED and connect
 * positive and negative sequence networks in opposition.</p>
 *
 * <p>For balanced systems (Z1 = Z2): I"k_LL = sqrt(3)/2 * I"k_LLL</p>
 *
 * @see com.powsybl.sc.validation.networks.Iec60909Networks#createSection31Network()
 * @author GridMV Validation Team
 */
class Iec60909Section31LlTest extends AbstractSection31Test {

    /**
     * Test LL (phase-to-phase) fault at bus B3.
     *
     * <p>Expected I"k = 67.52 kA from IEC 60909:2016 Section 3.1.</p>
     * <p>Uses BIPHASED fault type with ShortCircuitUnbalancedEngine.</p>
     */
    @Test
    void testB3IkLl() {
        runAndAssertFaultTest("IEC_60909_3.1_LL_B3", ShortCircuitFault.ShortCircuitType.BIPHASED);
    }
}
