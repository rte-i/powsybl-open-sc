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
 * IEC 60909:2016 Section 3.1 LG (Single-Phase-to-Ground) Fault Validation Tests.
 *
 * @author GridMV Validation Team
 */
class Iec60909Section31LgTest extends AbstractSection31Test {

    @Test
    void testB3IkLg() {
        runAndAssertFaultTest("IEC_60909_3.1_LG_B3", ShortCircuitFault.ShortCircuitType.MONOPHASED);
    }
}
