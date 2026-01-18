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
class Iec60909Section31LlgTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(Iec60909Section31LlgTest.class);

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
     * Test LLG (double-phase-to-ground) fault at bus B3.
     *
     * <p>Expected I"k = 52.37 kA from IEC 60909:2016 Section 3.1.</p>
     * <p>Uses BIPHASED_GROUND fault type with ShortCircuitUnbalancedEngine.</p>
     */
    @Test
    void testB3IkLlg() {
        List<TestCase> testCases = TestCaseLoader.loadTestCases(REFERENCE_DATA_PATH);
        TestCase testCase = findTestCase(testCases, "IEC_60909_3.1_LLG_B3");

        LOGGER.info("Running LLG fault test: {} at bus {}", testCase.getTestId(), testCase.getBusId());

        Network network = Iec60909Networks.createSection31Network();

        List<ShortCircuitFault> faultList = new ArrayList<>();
        ShortCircuitFault llgFault = new ShortCircuitFault(
                testCase.getBusId(),
                testCase.getTestId(),
                0.0, 0.0,
                ShortCircuitFault.ShortCircuitType.BIPHASED_GROUND
        );
        faultList.add(llgFault);

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

        double actualIkKa = scEngine.getResultsPerFault().get(llgFault).getIk().getKey();

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

    private TestCase findTestCase(List<TestCase> testCases, String testId) {
        return testCases.stream()
                .filter(tc -> tc.getTestId().equals(testId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Test case not found: " + testId));
    }
}
