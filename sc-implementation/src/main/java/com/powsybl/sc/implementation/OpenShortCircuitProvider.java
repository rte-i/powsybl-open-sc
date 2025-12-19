/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.implementation;

import com.google.auto.service.AutoService;
import com.google.common.base.Stopwatch;
import com.powsybl.computation.ComputationManager;
import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.math.matrix.SparseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.sc.extensions.BranchFaultSpec;
import com.powsybl.sc.extensions.ShortCircuitFaultSpecExtension;
import com.powsybl.sc.extensions.ShortCircuitStudyOptionsExtension;
import com.powsybl.sc.util.FeedersAtBusResult;
import com.powsybl.security.LimitViolation;
import com.powsybl.shortcircuit.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
@AutoService(ShortCircuitAnalysisProvider.class)
public class OpenShortCircuitProvider implements ShortCircuitAnalysisProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenShortCircuitProvider.class);

    private static final double BRANCH_POSITION_EPS = 1e-9;

    private final MatrixFactory matrixFactory;

    public OpenShortCircuitProvider() {
        this(new SparseMatrixFactory());
    }

    public OpenShortCircuitProvider(MatrixFactory matrixFactory) {
        this.matrixFactory = Objects.requireNonNull(matrixFactory);
    }

    @Override
    public String getName() {
        return "OpenShortCircuit";
    }

    @Override
    public String getVersion() {
        return "0.1";
    }

    @Override
    public CompletableFuture<ShortCircuitAnalysisResult> run(Network network, List<Fault> faults, ShortCircuitParameters parameters, ComputationManager computationManager, List<FaultParameters> faultParameters) {

        Objects.requireNonNull(network);
        Objects.requireNonNull(parameters);
        Stopwatch stopwatch = Stopwatch.createStarted();

        // building of fault lists
        List<ShortCircuitFault> faultsList = new ArrayList<>();
        Map<ShortCircuitFault, Fault> scFaultToFault = new HashMap<>(); // for now we use this map to get the correspondence between short circuit provider and internal modelling of fault

        ShortCircuitStudyOptionsExtension studyOptions = network.getExtension(ShortCircuitStudyOptionsExtension.class);
        ShortCircuitStudyOptionsExtension.Norm norm = studyOptions != null ? studyOptions.getNorm() : ShortCircuitStudyOptionsExtension.Norm.IEC_60909;
        ShortCircuitStudyOptionsExtension.Period period = studyOptions != null ? studyOptions.getPeriod() : ShortCircuitStudyOptionsExtension.Period.SUB_TRANSIENT;
        ShortCircuitStudyOptionsExtension.VoltageProfile voltageProfileType = studyOptions != null ? studyOptions.getVoltageProfile() : ShortCircuitStudyOptionsExtension.VoltageProfile.NOMINAL;
        boolean useCalculatedVoltageProfile = voltageProfileType == ShortCircuitStudyOptionsExtension.VoltageProfile.CALCULATED;

        LoadFlowParameters loadFlowParameters = new LoadFlowParameters();
        if (useCalculatedVoltageProfile) {
            LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(matrixFactory));
            loadFlowRunner.run(network, loadFlowParameters);
        }

        ShortCircuitEngineParameters.VoltageProfileType voltageProfile = toVoltageProfileType(voltageProfileType);

        // Selective or Systematic short circuit analysis
        ShortCircuitEngineParameters.AnalysisType at = ShortCircuitEngineParameters.AnalysisType.SELECTIVE;

        // selection of the period of analysis
        ShortCircuitEngineParameters.PeriodType periodType = toPeriodType(period);

        ShortCircuitNorm shortCircuitNorm = createShortCircuitNorm(norm);

        ShortCircuitEngineParameters scbParameters = new ShortCircuitEngineParameters(loadFlowParameters, matrixFactory, at, faultsList, useCalculatedVoltageProfile, voltageProfile, false, periodType, shortCircuitNorm);

        ShortCircuitFaultSpecExtension faultSpecExtension = network.getExtension(ShortCircuitFaultSpecExtension.class);
        BranchFaultSpecificationResolver branchFaultSpecificationResolver = new BranchFaultSpecificationResolver(faultSpecExtension);
        List<FaultProcessingResult> faultProcessingResults = processFaults(network, faults, faultsList, scFaultToFault,
                branchFaultSpecificationResolver, scbParameters);
        boolean existBalancedFaults = faultsList.stream().anyMatch(scFault -> scFault.getType() == ShortCircuitFault.ShortCircuitType.TRIPHASED_GROUND);
        boolean existUnbalancedFaults = faultsList.stream().anyMatch(scFault -> scFault.getType() != ShortCircuitFault.ShortCircuitType.TRIPHASED_GROUND);

        // lists to store the results
        List<FaultResult> faultResults = new ArrayList<>();

        if (existBalancedFaults) {
            runBalancedAnalysis(network, scbParameters, scFaultToFault, faultResults);
        }

        if (existUnbalancedFaults) {
            runUnbalancedAnalysis(network, scbParameters, scFaultToFault, faultResults);
        }

        addFailureResults(faultResults, faultProcessingResults);

        LOGGER.info("Short circuit calculation done in {} ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));

        ShortCircuitAnalysisResult analysisResult = new ShortCircuitAnalysisResult(faultResults);
        List<String> diagnostics = faultProcessingResults.stream()
                .filter(FaultProcessingResult::isFailure)
                .map(FaultProcessingResult::getDiagnostics)
                .toList();
        analysisResult.addExtension(ShortCircuitStudyReport.class, new ShortCircuitStudyReport(analysisResult, norm, period, voltageProfileType, diagnostics));

        return CompletableFuture.completedFuture(analysisResult);
    }

    public void runUnbalancedAnalysis(Network network, ShortCircuitEngineParameters scbParameters, Map<ShortCircuitFault, Fault> scFaultToFault, List<FaultResult> faultResults) {
        ShortCircuitUnbalancedEngine scuEngine = new ShortCircuitUnbalancedEngine(network, scbParameters);
        scuEngine.run();

        // the results per faults might be inconsistent if many busses per voltage level
        // TODO : see how this could be improved by allowing results per electrical bus on the short circuit provider
        for (Map.Entry<ShortCircuitFault, ShortCircuitResult> scResult : scuEngine.resultsPerFault.entrySet()) {
            ShortCircuitFault scFault = scResult.getKey();

            double iccMagnitude = scResult.getValue().getIcc().getKey();

            Fault fault = scFaultToFault.get(scFault);

            List<FeederResult> feederResults = new ArrayList<>();
            List<LimitViolation> limitViolations = new ArrayList<>();
            MagnitudeFaultResult magnitudeFaultResult = new MagnitudeFaultResult(fault, 0., feederResults, limitViolations, iccMagnitude, FaultResult.Status.SUCCESS);
            faultResults.add(magnitudeFaultResult);
        }
    }

    public void runBalancedAnalysis(Network network, ShortCircuitEngineParameters scbParameters, Map<ShortCircuitFault, Fault> scFaultToFault, List<FaultResult> faultResults) {
        ShortCircuitBalancedEngine scbEngine = new ShortCircuitBalancedEngine(network, scbParameters);
        scbEngine.run();

        // the results per faults might be inconsistent if many busses per voltage level
        // TODO : see how this could be improved by allowing results per electrical bus on the short circuit provider
        for (Map.Entry<ShortCircuitFault, ShortCircuitResult> scFaultResult : scbEngine.resultsPerFault.entrySet()) {
            ShortCircuitFault scFault = scFaultResult.getKey();
            ShortCircuitResult scResult = scFaultResult.getValue();

            double iccMagnitude = scResult.getIk().getKey();
            double iccAngle = scResult.getIk().getValue();

            Fault fault = scFaultToFault.get(scFault);

            List<FeederResult> feederResultsProvider = new ArrayList<>();
            fillFeederResults(feederResultsProvider, scResult);

            List<LimitViolation> limitViolations = new ArrayList<>();

            MagnitudeFaultResult magnitudeFaultResult = new MagnitudeFaultResult(fault, 0., feederResultsProvider, limitViolations, iccMagnitude, FaultResult.Status.SUCCESS);
            faultResults.add(magnitudeFaultResult);
        }
    }

    public void fillFeederResults(List<FeederResult> feederResultsProvider, ShortCircuitResult scResult) {
        Map<LfBus, FeedersAtBusResult> feedersAtBusResults = scResult.getFeedersAtBusResultsDirect();
        if (feedersAtBusResults == null || feedersAtBusResults.isEmpty()) {
            return;
        }
        for (Map.Entry<LfBus, FeedersAtBusResult> busAndFeedersAtBusResult : feedersAtBusResults.entrySet()) {
            LfBus lfBus = busAndFeedersAtBusResult.getKey();
            FeedersAtBusResult feedersAtBusResult = busAndFeedersAtBusResult.getValue();
            for (com.powsybl.sc.util.FeederResult feederResult : feedersAtBusResult.getBusFeedersResult()) {
                double ix = feederResult.getIxContribution();
                double iy = feederResult.getIyContribution();

                double magnitude = Math.sqrt(3. * (ix * ix + iy * iy)) * 100. / lfBus.getNominalV(); // same dimension as Ik3

                String feederId = lfBus.getId() + "_" + feederResult.getFeeder().getId();

                MagnitudeFeederResult magnitudeFeederResult = new MagnitudeFeederResult(feederId, magnitude);
                feederResultsProvider.add(magnitudeFeederResult);
            }
        }

    }

    private List<FaultProcessingResult> processFaults(Network network, List<Fault> faults, List<ShortCircuitFault> balancedFaultsList,
                                                      Map<ShortCircuitFault, Fault> scFaultToFault,
                                                      BranchFaultSpecificationResolver branchFaultSpecificationResolver,
                                                      ShortCircuitEngineParameters engineParameters) {
        List<FaultProcessingResult> faultProcessingResults = new ArrayList<>();
        for (Fault fault : faults) {
            FaultProcessingResult result = toShortCircuitFault(network, fault, branchFaultSpecificationResolver, engineParameters);
            faultProcessingResults.add(result);
            if (result.isReady()) {
                ShortCircuitFault sc = result.getShortCircuitFault();
                balancedFaultsList.add(sc);
                scFaultToFault.put(sc, fault);
            } else {
                LOGGER.warn(result.getDiagnostics());
            }
        }
        return faultProcessingResults;
    }

    private FaultProcessingResult toShortCircuitFault(Network network, Fault fault,
                                                     BranchFaultSpecificationResolver branchFaultSpecificationResolver,
                                                     ShortCircuitEngineParameters engineParameters) {
        if (fault.getConnectionType() == Fault.ConnectionType.PARALLEL) {
            return FaultProcessingResult.failure(fault, String.format("Short circuit connection of type PARALLEL not yet supported, fault: %s is ignored", fault.getId()));
        }

        ShortCircuitFault.ShortCircuitType scType;
        if (fault.getFaultType() == Fault.FaultType.SINGLE_PHASE) {
            scType = ShortCircuitFault.ShortCircuitType.MONOPHASED;
        } else if (fault.getFaultType() == Fault.FaultType.THREE_PHASE) {
            scType = ShortCircuitFault.ShortCircuitType.TRIPHASED_GROUND;
        } else {
            return FaultProcessingResult.failure(fault, String.format("Short circuit of unknown type, fault: %s is ignored", fault.getId()));
        }

        double rFault = fault.getRToGround();
        double xFault = fault.getXToGround();

        if (fault.getType() == Fault.Type.BRANCH) {
            BranchFaultSpecificationResolver.Resolution resolution = branchFaultSpecificationResolver.resolve(fault, network);
            if (resolution.getStatus() == BranchFaultSpecificationResolver.Resolution.Status.FAILURE) {
                return FaultProcessingResult.failure(fault, resolution.buildDiagnosticsMessage("Branch fault ignored"));
            }
            Optional<Bus> boundaryBus = findBoundaryBus(resolution, network);
            if (boundaryBus.isPresent()) {
                Bus bus = boundaryBus.get();
                ShortCircuitFault sc = new ShortCircuitFault(bus.getId(), bus.getId(), rFault, xFault, scType);
                return FaultProcessingResult.ready(fault, sc);
            }
            engineParameters.addBranchFaultContext(new BranchFaultContext(fault.getId(), resolution.getBranchId(),
                    resolution.getPositionAlpha(), resolution.getReferenceSide()));
            ShortCircuitFault sc = new ShortCircuitFault(resolution.getBranchId(), fault.getId(), rFault, xFault, scType);
            return FaultProcessingResult.ready(fault, sc);
        }

        String elementId = fault.getElementId();
        Bus bus = network.getBusBreakerView().getBus(elementId);
        if (bus == null) {
            return FaultProcessingResult.failure(fault, String.format("Short circuit element '%s' not found, fault: %s is ignored", elementId, fault.getId()));
        }
        ShortCircuitFault sc = new ShortCircuitFault(bus.getId(), bus.getId(), rFault, xFault, scType);
        return FaultProcessingResult.ready(fault, sc);
    }

    private void addFailureResults(List<FaultResult> faultResults, List<FaultProcessingResult> processingResults) {
        for (FaultProcessingResult processingResult : processingResults) {
            if (processingResult.getStatus() == FaultProcessingResult.Status.FAILURE) {
                MagnitudeFaultResult failureResult = new MagnitudeFaultResult(processingResult.getFault(), FaultResult.Status.FAILURE);
                if (processingResult.getDiagnostics() != null) {
                    failureResult.addExtension(FaultProcessingDiagnostic.class, new FaultProcessingDiagnostic(failureResult, processingResult.getDiagnostics()));
                }
                faultResults.add(failureResult);
            }
        }
    }

    private Optional<Bus> findBoundaryBus(BranchFaultSpecificationResolver.Resolution resolution, Network network) {
        double alpha = resolution.getPositionAlpha();
        if (alpha > BRANCH_POSITION_EPS && alpha < 1.0 - BRANCH_POSITION_EPS) {
            return Optional.empty();
        }
        Line line = network.getLine(resolution.getBranchId());
        if (line == null) {
            return Optional.empty();
        }
        Bus bus1 = line.getTerminal1().getBusBreakerView().getBus();
        Bus bus2 = line.getTerminal2().getBusBreakerView().getBus();
        if (bus1 == null || bus2 == null) {
            return Optional.empty();
        }
        boolean referenceIsBus1 = resolution.getReferenceSide() == BranchFaultSpec.BranchSide.FROM;
        if (alpha <= BRANCH_POSITION_EPS) {
            return Optional.of(referenceIsBus1 ? bus1 : bus2);
        } else if (alpha >= 1.0 - BRANCH_POSITION_EPS) {
            return Optional.of(referenceIsBus1 ? bus2 : bus1);
        }
        return Optional.empty();
    }

    private ShortCircuitEngineParameters.VoltageProfileType toVoltageProfileType(ShortCircuitStudyOptionsExtension.VoltageProfile voltageProfile) {
        return switch (voltageProfile) {
            case CALCULATED -> ShortCircuitEngineParameters.VoltageProfileType.CALCULATED;
            case NOMINAL -> ShortCircuitEngineParameters.VoltageProfileType.NOMINAL;
        };
    }

    private ShortCircuitEngineParameters.PeriodType toPeriodType(ShortCircuitStudyOptionsExtension.Period period) {
        return switch (period) {
            case SUB_TRANSIENT -> ShortCircuitEngineParameters.PeriodType.SUB_TRANSIENT;
            case TRANSIENT -> ShortCircuitEngineParameters.PeriodType.TRANSIENT;
            case STEADY_STATE -> ShortCircuitEngineParameters.PeriodType.STEADY_STATE;
        };
    }

    private ShortCircuitNorm createShortCircuitNorm(ShortCircuitStudyOptionsExtension.Norm norm) {
        return switch (norm) {
            case IEC_60909 -> new ShortCircuitNormIec();
            case NONE -> new ShortCircuitNormNone();
        };
    }

    private static final class FaultProcessingResult {
        enum Status {
            READY,
            FAILURE
        }

        private final Fault fault;
        private final ShortCircuitFault shortCircuitFault;
        private final Status status;
        private final String diagnostics;

        private FaultProcessingResult(Fault fault, ShortCircuitFault shortCircuitFault, Status status, String diagnostics) {
            this.fault = Objects.requireNonNull(fault);
            this.shortCircuitFault = shortCircuitFault;
            this.status = Objects.requireNonNull(status);
            this.diagnostics = diagnostics;
        }

        static FaultProcessingResult ready(Fault fault, ShortCircuitFault shortCircuitFault) {
            return new FaultProcessingResult(fault, Objects.requireNonNull(shortCircuitFault), Status.READY, null);
        }

        static FaultProcessingResult failure(Fault fault, String diagnostics) {
            return new FaultProcessingResult(fault, null, Status.FAILURE, Objects.requireNonNull(diagnostics));
        }

        Fault getFault() {
            return fault;
        }

        ShortCircuitFault getShortCircuitFault() {
            return shortCircuitFault;
        }

        Status getStatus() {
            return status;
        }

        String getDiagnostics() {
            return diagnostics;
        }

        boolean isReady() {
            return status == Status.READY;
        }

        boolean isFailure() {
            return status == Status.FAILURE;
        }
    }
}
