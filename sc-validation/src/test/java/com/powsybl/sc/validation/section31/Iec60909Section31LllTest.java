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
import com.powsybl.sc.implementation.ShortCircuitBalancedEngine;
import com.powsybl.sc.implementation.ShortCircuitEngineParameters;
import com.powsybl.sc.implementation.ShortCircuitFault;
import com.powsybl.sc.implementation.ShortCircuitNormIec;
import com.powsybl.sc.validation.networks.Iec60909Networks;
import com.powsybl.sc.validation.util.TestCase;
import com.powsybl.sc.validation.util.TestCaseLoader;
import com.powsybl.sc.validation.util.ValidationAssertions;
import com.powsybl.sc.validation.util.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * IEC 60909:2016 Section 3.1 LLL (Three-Phase Symmetrical) Fault Validation Tests.
 *
 * <p>This test class validates three-phase balanced short-circuit calculations against
 * IEC 60909:2016 Section 3.1 reference values. The LLL fault is the most common type
 * of fault calculation as it produces the highest symmetrical fault currents.</p>
 *
 * <p>Note: This class does not extend AbstractSection31Test because LLL (three-phase
 * symmetrical) faults use ShortCircuitBalancedEngine, whereas the unbalanced fault types
 * (LG, LL, LLG) in AbstractSection31Test use ShortCircuitUnbalancedEngine.</p>
 *
 * <p>Reference: IEC 60909:2016, Section 3.1</p>
 *
 * @author GridMV Validation Team
 */
class Iec60909Section31LllTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(Iec60909Section31LllTest.class);
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
     * Test three-phase symmetrical (LLL) fault at bus B3.
     * Expected I"k = 34.62 kA per IEC 60909:2016 Section 3.1.
     */
    @Test
    void testB3IkNominalVoltage() {
        // Load test cases from JSON reference data
        List<TestCase> testCases = TestCaseLoader.loadTestCases(REFERENCE_DATA_PATH);
        TestCase testCase = TestCaseLoader.findTestCase(testCases, "IEC_60909_3.1_LLL_B3");

        LOGGER.info("Running LLL fault validation: {}", testCase.getTestId());

        // Create network using the factory
        Network network = Iec60909Networks.createSection31Network();

        // Define fault at the specified bus
        ShortCircuitFault lllFault = new ShortCircuitFault(
            testCase.getBusId(),
            testCase.getTestId(),
            0.0, 0.0,
            ShortCircuitFault.ShortCircuitType.TRIPHASED_GROUND
        );
        List<ShortCircuitFault> faultList = List.of(lllFault);

        // Configure engine parameters for IEC 60909 calculation
        ShortCircuitEngineParameters.PeriodType periodType =
            ShortCircuitEngineParameters.PeriodType.SUB_TRANSIENT;
        ShortCircuitNormIec normIec = new ShortCircuitNormIec();
        ShortCircuitEngineParameters params = new ShortCircuitEngineParameters(
            loadFlowParameters,
            matrixFactory,
            ShortCircuitEngineParameters.AnalysisType.SELECTIVE,
            faultList,
            true, // withFeederResult
            ShortCircuitEngineParameters.VoltageProfileType.NOMINAL,
            false, // ignoreShunts
            periodType,
            normIec
        );

        // Run short-circuit calculation using balanced engine
        ShortCircuitBalancedEngine scEngine = new ShortCircuitBalancedEngine(network, params);
        scEngine.run();

        // Extract fault current result
        double actualIkKa = scEngine.getResultsPerFault().get(lllFault).getIk().getKey();

        LOGGER.info("LLL fault at {}: Calculated Ik = {} kA", testCase.getBusId(), actualIkKa);

        // Validate result against expected value using ValidationAssertions
        ValidationResult result = ValidationAssertions.assertShortCircuitResult(
            actualIkKa,
            testCase.getExpectedIkKa(),
            testCase.getTolerancePercent(),
            testCase.getTestId()
        );

        LOGGER.info("{}", result);
        assertTrue(result.isPassed(), result.toString());
    }
}
