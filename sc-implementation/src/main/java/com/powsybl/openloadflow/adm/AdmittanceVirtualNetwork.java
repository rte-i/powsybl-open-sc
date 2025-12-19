/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.adm;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.LimitType;
import com.powsybl.iidm.network.LoadingLimits;
import com.powsybl.iidm.network.TwoSides;
import com.powsybl.openloadflow.network.LfAsymLine;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfNetworkUpdateReport;
import com.powsybl.openloadflow.network.LfNetworkStateUpdateParameters;
import com.powsybl.openloadflow.network.PiModel;
import com.powsybl.openloadflow.network.SimplePiModel;
import com.powsybl.openloadflow.network.impl.AbstractImpedantLfBranch;
import com.powsybl.openloadflow.network.impl.AbstractLfBus;
import com.powsybl.openloadflow.network.LfNetworkParameters;
import com.powsybl.openloadflow.sa.LimitReductionManager;
import com.powsybl.sc.util.extensions.ScLoad;
import com.powsybl.sc.util.extensions.ShortCircuitExtensions;
import com.powsybl.security.ViolationLocation;
import com.powsybl.security.results.BranchResult;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;

/**
 * Describes a virtual network used when modelling branch faults without mutating the original {@link LfNetwork}.
 */
public final class AdmittanceVirtualNetwork {

    private final LfNetwork network;
    private final List<LfBus> buses;
    private final List<LfBranch> branches;
    private final Map<String, BranchFaultVirtualNodeInstance> faultInstances;

    private AdmittanceVirtualNetwork(LfNetwork network, List<LfBus> buses, List<LfBranch> virtualBranches,
                                     List<LfBranch> branchList, Map<String, BranchFaultVirtualNodeInstance> faultInstances) {
        this.network = Objects.requireNonNull(network);
        this.buses = Objects.requireNonNull(buses);
        this.faultInstances = Objects.requireNonNull(faultInstances);
        this.branches = new ArrayList<>(Objects.requireNonNull(branchList));
        this.branches.addAll(virtualBranches);
    }

    public static AdmittanceVirtualNetwork empty(LfNetwork network) {
        return new AdmittanceVirtualNetwork(network, List.of(), List.of(), network.getBranches(), Collections.emptyMap());
    }

    public List<LfBus> getBuses() {
        return buses.isEmpty() ? network.getBuses() : mergeCollections(network.getBuses(), buses);
    }

    public List<LfBranch> getBranches() {
        return branches.isEmpty() ? network.getBranches() : branches;
    }

    public LfBus getBusById(String busId) {
        Objects.requireNonNull(busId);
        LfBus bus = network.getBusById(busId);
        if (bus != null) {
            return bus;
        }
        for (LfBus virtualBus : buses) {
            if (virtualBus.getId().equals(busId)) {
                return virtualBus;
            }
        }
        return null;
    }

    public Optional<LfBus> getFaultBus(String faultId) {
        return Optional.ofNullable(faultInstances.get(faultId)).map(BranchFaultVirtualNodeInstance::getBus);
    }

    public Optional<BranchFaultVirtualNodeInstance> getFaultInstance(String faultId) {
        return Optional.ofNullable(faultInstances.get(faultId));
    }

    public Collection<BranchFaultVirtualNodeInstance> getFaultInstances() {
        return faultInstances.values();
    }

    private static <T> List<T> mergeCollections(List<T> original, List<T> additions) {
        if (additions.isEmpty()) {
            return original;
        }
        List<T> merged = new ArrayList<>(original.size() + additions.size());
        merged.addAll(original);
        merged.addAll(additions);
        return merged;
    }

    public static Builder builder(LfNetwork network) {
        return new Builder(network);
    }

    public static final class Builder {

        private static final double EPS = 1e-9;

        private final LfNetwork network;
        private final LfNetworkParameters parameters;
        private final List<LfBus> buses = new ArrayList<>();
        private final List<LfBranch> virtualBranches = new ArrayList<>();
        private final List<LfBranch> branchList = new ArrayList<>();
        private final Map<String, BranchFaultVirtualNodeInstance> faultInstances = new LinkedHashMap<>();
        private final Set<String> replacedBranches = new HashSet<>();
        private int nextBusNum;
        private int nextBranchNum;

        public Builder(LfNetwork network) {
            this.network = Objects.requireNonNull(network);
            this.parameters = new LfNetworkParameters();
            this.branchList.addAll(network.getBranches());
            this.nextBusNum = network.getBuses().size();
            this.nextBranchNum = network.getBranches().size();
        }

        public BranchFaultVirtualNodeInstance add(BranchFaultVirtualNode node) {
            Objects.requireNonNull(node);
            if (faultInstances.containsKey(node.getFaultId())) {
                throw new PowsyblException("Fault '" + node.getFaultId() + "' already registered");
            }
            LfBranch branch = Objects.requireNonNull(network.getBranchById(node.getBranchId()),
                    () -> "Branch '" + node.getBranchId() + "' not found");
            if (branch.getBus1() == null || branch.getBus2() == null) {
                throw new PowsyblException("Branch '" + branch.getId() + "' is disconnected");
            }
            double rho = branch.getPiModel().getR1();
            double a1 = branch.getPiModel().getA1();
            if (Math.abs(rho - 1.0) > EPS || Math.abs(a1) > EPS) {
                throw new PowsyblException("Branch '" + branch.getId() + "' uses a tap or phase shift which is not supported for branch faults");
            }
            double portionFromBus1 = node.getPortionFromBus1();
            double portionFromBus2 = 1.0 - portionFromBus1;
            LfBus targetBus;
            List<VirtualAdmittanceBranch> createdSegments = List.of();
            if (node.isCloseToBus(portionFromBus1)) {
                targetBus = branch.getBus1();
            } else if (node.isCloseToBus(portionFromBus2)) {
                targetBus = branch.getBus2();
            } else {
                VirtualAdmittanceBus virtualBus = new VirtualAdmittanceBus(network,
                        buildBusId(branch, node),
                        getReferenceBus(branch, node.getReferenceSide()),
                        parameters);
                virtualBus.setNum(nextBusNum++);
                buses.add(virtualBus);
                targetBus = virtualBus;
                replaceBranch(branch);
                Pair<VirtualAdmittanceBranch, VirtualAdmittanceBranch> segments = createSegments(branch, virtualBus, portionFromBus1, portionFromBus2, node);
                createdSegments = List.of(segments.getLeft(), segments.getRight());
                branchList.addAll(createdSegments);
                virtualBranches.addAll(createdSegments);
            }
            BranchFaultVirtualNodeInstance instance = new BranchFaultVirtualNodeInstance(node, targetBus, createdSegments);
            faultInstances.put(node.getFaultId(), instance);
            return instance;
        }

        private void replaceBranch(LfBranch branch) {
            if (replacedBranches.add(branch.getId())) {
                branchList.remove(branch);
            }
        }

        private static String buildBusId(LfBranch branch, BranchFaultVirtualNode node) {
            return branch.getId() + "_FAULT_" + node.getFaultId();
        }

        private static LfBus getReferenceBus(LfBranch branch, TwoSides side) {
            return side == TwoSides.ONE ? branch.getBus1() : branch.getBus2();
        }

        private Pair<VirtualAdmittanceBranch, VirtualAdmittanceBranch> createSegments(LfBranch source, LfBus virtualBus,
                                                                                      double portionFromBus1, double portionFromBus2,
                                                                                      BranchFaultVirtualNode node) {
            SimplePiModel firstPi = createSegmentPiModel(source.getPiModel(), portionFromBus1, true, false);
            SimplePiModel secondPi = createSegmentPiModel(source.getPiModel(), portionFromBus2, false, true);
            VirtualAdmittanceBranch first = new VirtualAdmittanceBranch(network, source.getBus1(), virtualBus, firstPi,
                    source.getBranchType(), source.getId() + ":" + node.getFaultId() + ":A", parameters);
            first.setNum(nextBranchNum++);
            VirtualAdmittanceBranch second = new VirtualAdmittanceBranch(network, virtualBus, source.getBus2(), secondPi,
                    source.getBranchType(), source.getId() + ":" + node.getFaultId() + ":B", parameters);
            second.setNum(nextBranchNum++);
            copyAsymLine(source, portionFromBus1, portionFromBus2, first, second);
            return Pair.of(first, second);
        }

        private static SimplePiModel createSegmentPiModel(PiModel source, double portion,
                                                          boolean keepBus1Shunt, boolean keepBus2Shunt) {
            SimplePiModel pi = new SimplePiModel()
                    .setR1(source.getR1())
                    .setA1(source.getA1())
                    .setR(source.getR() * portion)
                    .setX(source.getX() * portion);
            if (keepBus1Shunt) {
                pi.setG1(source.getG1()).setB1(source.getB1());
            }
            if (keepBus2Shunt) {
                pi.setG2(source.getG2()).setB2(source.getB2());
            }
            return pi;
        }

        private static void copyAsymLine(LfBranch source, double portionFromBus1, double portionFromBus2,
                                         VirtualAdmittanceBranch first, VirtualAdmittanceBranch second) {
            LfAsymLine asymLine = source.getAsymLine();
            if (asymLine == null) {
                return;
            }
            first.setAsymLine(createSegmentAsymLine(asymLine, portionFromBus1, true));
            second.setAsymLine(createSegmentAsymLine(asymLine, portionFromBus2, false));
        }

        private static LfAsymLine createSegmentAsymLine(LfAsymLine source, double portion, boolean keepBus1Shunt) {
            SimplePiModel zero = createSegmentPiModel(source.getPiZeroComponent(), portion, keepBus1Shunt, !keepBus1Shunt);
            SimplePiModel positive = createSegmentPiModel(source.getPiPositiveComponent(), portion, keepBus1Shunt, !keepBus1Shunt);
            SimplePiModel negative = createSegmentPiModel(source.getPiNegativeComponent(), portion, keepBus1Shunt, !keepBus1Shunt);
            return new LfAsymLine(zero, positive, negative,
                    source.isPhaseOpenA(), source.isPhaseOpenB(), source.isPhaseOpenC());
        }

        public AdmittanceVirtualNetwork build() {
            return new AdmittanceVirtualNetwork(network, buses, virtualBranches, branchList, faultInstances);
        }
    }

    public static final class BranchFaultVirtualNodeInstance {

        private final BranchFaultVirtualNode definition;
        private final LfBus lfBus;
        private final List<LfBranch> segments;

        private BranchFaultVirtualNodeInstance(BranchFaultVirtualNode definition, LfBus lfBus, List<? extends LfBranch> segments) {
            this.definition = definition;
            this.lfBus = lfBus;
            this.segments = Collections.unmodifiableList(new ArrayList<>(segments));
        }

        public BranchFaultVirtualNode getDefinition() {
            return definition;
        }

        public LfBus getBus() {
            return lfBus;
        }

        public List<LfBranch> getSegments() {
            return segments;
        }
    }

    private static final class VirtualAdmittanceBus extends AbstractLfBus {

        private final String id;
        private final String voltageLevelId;
        private final double nominalV;

        private VirtualAdmittanceBus(LfNetwork network, String id, LfBus referenceBus, LfNetworkParameters parameters) {
            super(network, referenceBus.getV(), referenceBus.getAngle(), parameters);
            this.id = Objects.requireNonNull(id);
            this.voltageLevelId = referenceBus.getVoltageLevelId();
            this.nominalV = referenceBus.getNominalV();
            Object loadProperty = referenceBus.getProperty(ShortCircuitExtensions.PROPERTY_SHORT_CIRCUIT);
            if (loadProperty instanceof ScLoad scLoad) {
                setProperty(ShortCircuitExtensions.PROPERTY_SHORT_CIRCUIT,
                        new ScLoad(scLoad.getGdEquivalent(), scLoad.getBdEquivalent()));
            } else {
                setProperty(ShortCircuitExtensions.PROPERTY_SHORT_CIRCUIT, new ScLoad(0.0, 0.0));
            }
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public List<String> getOriginalIds() {
            return List.of(id);
        }

        @Override
        public String getVoltageLevelId() {
            return voltageLevelId;
        }

        @Override
        public boolean isFictitious() {
            return true;
        }

        @Override
        public double getNominalV() {
            return nominalV;
        }

        @Override
        public void updateState(LfNetworkStateUpdateParameters parameters) {
            // No IIDM element to update
        }

        @Override
        public Optional<com.powsybl.iidm.network.Country> getCountry() {
            return Optional.empty();
        }

        @Override
        public ViolationLocation getViolationLocation() {
            return null;
        }
    }

    private static final class VirtualAdmittanceBranch extends AbstractImpedantLfBranch {

        private final String id;
        private final BranchType branchType;

        private VirtualAdmittanceBranch(LfNetwork network, LfBus bus1, LfBus bus2, PiModel piModel,
                                        BranchType branchType, String id, LfNetworkParameters parameters) {
            super(network, bus1, bus2, piModel, parameters);
            this.branchType = branchType;
            this.id = Objects.requireNonNull(id);
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public List<String> getOriginalIds() {
            return List.of(id);
        }

        @Override
        public BranchType getBranchType() {
            return branchType;
        }

        @Override
        public List<LfLimit> getLimits1(LimitType type, LimitReductionManager limitReductionManager) {
            return Collections.emptyList();
        }

        @Override
        public List<LfLimit> getLimits2(LimitType type, LimitReductionManager limitReductionManager) {
            return Collections.emptyList();
        }

        @Override
        public boolean hasPhaseControllerCapability() {
            return false;
        }

        @Override
        public double[] getLimitReductions(TwoSides side, LimitReductionManager limitReductionManager, LoadingLimits limits) {
            return new double[0];
        }

        @Override
        public void updateState(LfNetworkStateUpdateParameters parameters, LfNetworkUpdateReport updateReport) {
            // No IIDM element to update
        }

        @Override
        public List<BranchResult> createBranchResult(double preContingencyBranchP1, double preContingencyBranchOfContingencyP1,
                                                     boolean createExtension) {
            return List.of();
        }

        @Override
        public void updateFlows(double p1, double q1, double p2, double q2) {
            // Synthetic branch, nothing to back-propagate
        }

    }
}
