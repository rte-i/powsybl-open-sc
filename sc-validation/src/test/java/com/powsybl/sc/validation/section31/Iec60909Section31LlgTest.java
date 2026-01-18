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
 * IEC 60909:2016 Section 3.1 LLG (Double-Phase-to-Ground) Fault Validation Tests.
 *
 * <p>Validates double-phase-to-ground short-circuit calculations against
 * IEC 60909 expected values using the ShortCircuitUnbalancedEngine.</p>
 *
 * <p>LLG faults use ShortCircuitFault.ShortCircuitType.BIPHASED_GROUND and connect
 * all three sequence networks (positive, negative, zero) in parallel at the fault point.</p>
 *
 * <p>Prerequisites:
 * <ul>
 *   <li>Network must have Fortescue extensions (LineFortescue, GeneratorFortescue)</li>
 *   <li>Balanced geometry assumption: Z1 = Z2</li>
 *   <li>Zero-sequence impedance data required for ground fault calculations</li>
 * </ul>
 * </p>
 *
 * @see com.powsybl.sc.validation.networks.Iec60909Networks#createSection31Network()
 * @author GridMV Validation Team
 */
class Iec60909Section31LlgTest extends AbstractSection31Test {

    /**
     * Test LLG (double-phase-to-ground) fault at bus B3.
     *
     * <p>Expected I"k = 52.37 kA from IEC 60909:2016 Section 3.1.</p>
     * <p>Uses BIPHASED_GROUND fault type with ShortCircuitUnbalancedEngine.</p>
     */
    @Test
    void testB3IkLlg() {
        runAndAssertFaultTest("IEC_60909_3.1_LLG_B3", ShortCircuitFault.ShortCircuitType.BIPHASED_GROUND);
    }
}
