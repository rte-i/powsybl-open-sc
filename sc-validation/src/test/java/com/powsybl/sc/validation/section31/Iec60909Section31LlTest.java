/*
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.sc.validation.section31;

import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.sc.implementation.ShortCircuitEngineParameters;
import com.powsybl.sc.implementation.ShortCircuitFault;
import com.powsybl.sc.implementation.ShortCircuitNormIec;
import com.powsybl.sc.implementation.ShortCircuitUnbalancedEngine;
import com.powsybl.sc.validation.networks.Iec60909Networks;
import com.powsybl.sc.validation.util.TestCase;
import com.powsybl.sc.validation.util.TestCaseLoader;
import com.powsybl.sc.validation.util.ValidationAssertions;
import com.powsybl.sc.validation.util.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

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
class Iec60909Section31LlTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(Iec60909Section31LlTest.class);

    private static final String REFERENCE_DATA_PATH = "reference-data/iec60909-section-3.1.json";

    private MatrixFactory matrixFactory;
    private LoadFlowParameters loadFlowParameters;

    @BeforeEach
    void setUp() {
        matrixFactory = new DenseMatrixFactory();
        loadFlowParameters = LoadFlowParameters.load();
        loadFlowParameters.setTwtSplitShuntAdmittance(true);
    }

    /**
     * Test LL (phase-to-phase) fault at bus B3.
     *
     * <p>Expected I"k = 67.52 kA from IEC 60909:2016 Section 3.1.</p>
     * <p>Uses BIPHASED fault type with ShortCircuitUnbalancedEngine.</p>
     */
    @Test
    void testB3IkLl() {
        List<TestCase> testCases = TestCaseLoader.loadTestCases(REFERENCE_DATA_PATH);
        TestCase testCase = TestCaseLoader.findTestCase(testCases, "IEC_60909_3.1_LL_B3");

        LOGGER.info("Running LL fault test: {} at bus {}", testCase.getTestId(), testCase.getBusId());

        Network network = Iec60909Networks.createSection31Network();

        List<ShortCircuitFault> faultList = new ArrayList<>();
        ShortCircuitFault llFault = new ShortCircuitFault(
                testCase.getBusId(),
                testCase.getTestId(),
                0.0, 0.0,
                ShortCircuitFault.ShortCircuitType.BIPHASED
        );
        faultList.add(llFault);

        ShortCircuitEngineParameters.PeriodType periodType =
                ShortCircuitEngineParameters.PeriodType.SUB_TRANSIENT;
        ShortCircuitNormIec normIec = new ShortCircuitNormIec();
        ShortCircuitEngineParameters params = new ShortCircuitEngineParameters(
                loadFlowParameters,
                matrixFactory,
                ShortCircuitEngineParameters.AnalysisType.SELECTIVE,
                faultList,
                false,
                ShortCircuitEngineParameters.VoltageProfileType.NOMINAL,
                false,
                periodType,
                normIec
        );

        ShortCircuitUnbalancedEngine scEngine = new ShortCircuitUnbalancedEngine(network, params);
        scEngine.run();

        double actualIkKa = scEngine.getResultsPerFault().get(llFault).getIk().getKey();

        LOGGER.info("Calculated I\"k = {} kA for {} fault at {}",
                String.format("%.4f", actualIkKa), testCase.getFaultType(), testCase.getBusId());

        ValidationResult result = ValidationAssertions.assertShortCircuitResult(
                actualIkKa,
                testCase.getExpectedIkKa(),
                testCase.getTolerancePercent(),
                testCase.getTestId()
        );

        assertTrue(result.isPassed(), result.toString());
    }
}
