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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Base class for IEC 60909:2016 Section 3.1 fault validation tests.
 *
 * <p>Provides common infrastructure for LG, LL, and LLG fault type tests including:
 * <ul>
 *   <li>Matrix factory and load flow parameters setup</li>
 *   <li>Test case loading from reference data</li>
 *   <li>Engine execution and result validation</li>
 * </ul>
 * </p>
 *
 * @author GridMV Validation Team
 */
abstract class AbstractSection31Test {

    protected static final String REFERENCE_DATA_PATH = "reference-data/iec60909-section-3.1.json";

    // Cached test cases - loaded once per test class hierarchy
    private static List<TestCase> cachedTestCases;

    protected MatrixFactory matrixFactory;
    protected LoadFlowParameters loadFlowParameters;

    @BeforeAll
    static void setUpTestCases() {
        if (cachedTestCases == null) {
            cachedTestCases = TestCaseLoader.loadTestCases(REFERENCE_DATA_PATH);
        }
    }

    @BeforeEach
    void setUp() {
        matrixFactory = new DenseMatrixFactory();
        loadFlowParameters = LoadFlowParameters.load();
        loadFlowParameters.setTwtSplitShuntAdmittance(true);
    }

    /**
     * Runs a fault calculation and validates the result against expected values.
     *
     * @param testCaseId the test case identifier to load from reference data
     * @param faultType the type of short circuit fault to simulate
     * @return the validation result
     */
    protected ValidationResult runFaultTest(String testCaseId, ShortCircuitFault.ShortCircuitType faultType) {
        TestCase testCase = TestCaseLoader.findTestCase(cachedTestCases, testCaseId);

        Network network = Iec60909Networks.createSection31Network();

        ShortCircuitFault fault = new ShortCircuitFault(
                testCase.getBusId(),
                testCase.getTestId(),
                0.0, 0.0,
                faultType
        );
        List<ShortCircuitFault> faultList = List.of(fault);

        ShortCircuitEngineParameters params = new ShortCircuitEngineParameters(
                loadFlowParameters,
                matrixFactory,
                ShortCircuitEngineParameters.AnalysisType.SELECTIVE,
                faultList,
                false,
                ShortCircuitEngineParameters.VoltageProfileType.NOMINAL,
                false,
                ShortCircuitEngineParameters.PeriodType.SUB_TRANSIENT,
                new ShortCircuitNormIec()
        );

        ShortCircuitUnbalancedEngine scEngine = new ShortCircuitUnbalancedEngine(network, params);
        scEngine.run();

        double actualIkKa = scEngine.getResultsPerFault().get(fault).getIk().getKey();

        return ValidationAssertions.assertShortCircuitResult(
                actualIkKa,
                testCase.getExpectedIkKa(),
                testCase.getTolerancePercent(),
                testCase.getTestId()
        );
    }

    /**
     * Runs a fault calculation and asserts it passes validation.
     *
     * @param testCaseId the test case identifier to load from reference data
     * @param faultType the type of short circuit fault to simulate
     */
    protected void runAndAssertFaultTest(String testCaseId, ShortCircuitFault.ShortCircuitType faultType) {
        ValidationResult result = runFaultTest(testCaseId, faultType);
        assertTrue(result.isPassed(), result.toString());
    }
}
