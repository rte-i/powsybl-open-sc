/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.implementation;

import com.powsybl.computation.ComputationManager;
import com.powsybl.computation.local.LocalComputationManager;
import com.powsybl.ieeecdf.converter.IeeeCdfNetworkFactory;
import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.TwoWindingsTransformer;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.sc.extensions.ShortCircuitFaultSpecExtensionAdder;
import com.powsybl.shortcircuit.*;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ShortCircuitBranchFaultIeeeTest {

    private static final DenseMatrixFactory MATRIX_FACTORY = new DenseMatrixFactory();
    private static final ShortCircuitAnalysisProvider PROVIDER = new OpenShortCircuitProvider(MATRIX_FACTORY);
    private static final ComputationManager CM = LocalComputationManager.getDefault();
    private static final double CURRENT_EPS = 1e-3;

    @Test
    void ieee14BranchFaultsMatchBusResults() {
        Network network = createIeee14Network();
        Map<String, Double> busCurrents = computeBusFaultCurrents(network);

        for (Line line : network.getLines()) {
            Bus bus1 = line.getTerminal1().getBusBreakerView().getBus();
            Bus bus2 = line.getTerminal2().getBusBreakerView().getBus();
            if (bus1 == null || bus2 == null) {
                continue;
            }
            String prefix = "IEEE_" + line.getId();
            String fromId = prefix + "_FROM";
            String toId = prefix + "_TO";
            String midId = prefix + "_MID";
            Map<String, MagnitudeFaultResult> branchResults = runBranchFaultBatch(prefix, line.getId());

            double bus1Current = requireBusCurrent(busCurrents, bus1.getId());
            double bus2Current = requireBusCurrent(busCurrents, bus2.getId());
            assertEquals(bus1Current, requireFaultCurrent(branchResults, fromId), CURRENT_EPS,
                    () -> "Mismatch at " + line.getId() + " alpha=0");
            assertEquals(bus2Current, requireFaultCurrent(branchResults, toId), CURRENT_EPS,
                    () -> "Mismatch at " + line.getId() + " alpha=1");

            double midCurrent = requireFaultCurrent(branchResults, midId);
            assertTrue(midCurrent > 0 && Double.isFinite(midCurrent),
                    () -> "Invalid mid-span current for " + line.getId());
        }
    }

    @Test
    void ieee14TransformerBranchFaultFails() {
        Network network = createIeee14Network();
        TwoWindingsTransformer transformer = network.getTwoWindingsTransformers().iterator().next();
        Fault branchFault = new BranchFault("IEEE_TFO", transformer.getId(), 0.5);

        ShortCircuitAnalysisResult result = runProvider(network, Collections.singletonList(branchFault));
        assertEquals(1, result.getFaultResults().size());
        FaultResult faultResult = result.getFaultResults().get(0);
        assertEquals(FaultResult.Status.FAILURE, faultResult.getStatus());
        FaultProcessingDiagnostic diagnostic = faultResult.getExtension(FaultProcessingDiagnostic.class);
        assertNotNull(diagnostic);
        assertTrue(diagnostic.getMessage().toLowerCase(Locale.ROOT).contains("transformer"));
    }

    @Test
    void ieee14BranchFaultPerformanceComparable() {
        Network busNetwork = createIeee14Network();
        List<Fault> busFaults = new ArrayList<>();
        for (Bus bus : busNetwork.getBusBreakerView().getBuses()) {
            busFaults.add(new BusFault("PERF_BUS_" + bus.getId(), bus.getId()));
        }
        Duration busDuration = measure(() -> runProvider(busNetwork, busFaults));

        long busPerFault = busFaults.isEmpty() ? 0 : busDuration.toNanos() / busFaults.size();

        Network branchNetwork = createIeee14Network();
        ShortCircuitFaultSpecExtensionAdder adder = branchNetwork.newExtension(ShortCircuitFaultSpecExtensionAdder.class);
        List<Fault> branchFaults = new ArrayList<>();
        for (Line line : branchNetwork.getLines()) {
            if (line.getTerminal1().getBusBreakerView().getBus() == null
                    || line.getTerminal2().getBusBreakerView().getBus() == null) {
                continue;
            }
            String faultId = "PERF_" + line.getId();
            adder.withBranchFault(faultId, line.getId(), 0.5);
            branchFaults.add(new BranchFault(faultId, line.getId(), 0.5));
        }
        adder.add();
        assertTrue(!branchFaults.isEmpty(), "No branch faults executed");
        Duration branchDuration = measure(() -> runProvider(branchNetwork, branchFaults));
        long branchPerFault = branchDuration.toNanos() / branchFaults.size();

        String summary = "Branch faults per-fault " + Duration.ofNanos(branchPerFault)
                + " vs bus per-fault " + Duration.ofNanos(busPerFault);
        assertTrue(branchPerFault <= busPerFault * 10 + 1, summary);
    }

    private static Network createIeee14Network() {
        Network network = IeeeCdfNetworkFactory.create14();
        LoadFlowParameters parameters = LoadFlowParameters.load();
        parameters.setTwtSplitShuntAdmittance(true);
        LoadFlow.run(network, parameters);
        return network;
    }

    private static Map<String, Double> computeBusFaultCurrents(Network network) {
        List<Fault> faults = new ArrayList<>();
        for (Bus bus : network.getBusBreakerView().getBuses()) {
            faults.add(new BusFault("BUS_" + bus.getId(), bus.getId()));
        }
        ShortCircuitAnalysisResult result = runProvider(network, faults);
        Map<String, Double> currents = new HashMap<>();
        for (FaultResult faultResult : result.getFaultResults()) {
            MagnitudeFaultResult magnitude = (MagnitudeFaultResult) faultResult;
            currents.put(faultResult.getFault().getElementId(), magnitude.getCurrent());
        }
        return currents;
    }

    private static Map<String, MagnitudeFaultResult> runFaults(Network network, List<Fault> faults) {
        ShortCircuitAnalysisResult result = runProvider(network, faults);
        Map<String, MagnitudeFaultResult> map = new HashMap<>();
        for (FaultResult faultResult : result.getFaultResults()) {
            assertEquals(FaultResult.Status.SUCCESS, faultResult.getStatus(),
                    () -> "Fault " + faultResult.getFault().getId() + " failed");
            map.put(faultResult.getFault().getId(), (MagnitudeFaultResult) faultResult);
        }
        return map;
    }

    private static ShortCircuitAnalysisResult runProvider(Network network, List<Fault> faults) {
        ShortCircuitParameters parameters = new ShortCircuitParameters();
        return PROVIDER.run(network, faults, parameters, CM, Collections.emptyList()).join();
    }

    private static Map<String, MagnitudeFaultResult> runBranchFaultBatch(String prefix, String branchId) {
        Map<String, MagnitudeFaultResult> results = new HashMap<>();
        results.put(prefix + "_FROM", runBranchFault(prefix + "_FROM", branchId, 0.0));
        results.put(prefix + "_TO", runBranchFault(prefix + "_TO", branchId, 1.0));
        results.put(prefix + "_MID", runBranchFault(prefix + "_MID", branchId, 0.5));
        return results;
    }

    private static MagnitudeFaultResult runBranchFault(String faultId, String branchId, double alpha) {
        Network branchNetwork = createIeee14Network();
        branchNetwork.newExtension(ShortCircuitFaultSpecExtensionAdder.class)
                .withBranchFault(faultId, branchId, alpha)
                .add();
        Map<String, MagnitudeFaultResult> results = runFaults(branchNetwork,
                Collections.singletonList(new BranchFault(faultId, branchId, alpha)));
        MagnitudeFaultResult result = results.get(faultId);
        assertNotNull(result, () -> "No result recorded for " + faultId);
        return result;
    }

    private static double requireFaultCurrent(Map<String, MagnitudeFaultResult> branchCurrents, String faultId) {
        MagnitudeFaultResult result = branchCurrents.get(faultId);
        assertNotNull(result, () -> "No branch fault result recorded for " + faultId);
        return result.getCurrent();
    }

    private static double requireBusCurrent(Map<String, Double> busCurrents, String busId) {
        Double value = busCurrents.get(busId);
        assertNotNull(value, () -> "No bus fault current recorded for " + busId);
        return value;
    }

    private static Duration measure(Runnable runnable) {
        long start = System.nanoTime();
        runnable.run();
        return Duration.ofNanos(System.nanoTime() - start);
    }
}
