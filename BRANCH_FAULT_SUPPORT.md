Branch Fault Support – Technical & Functional Notes
===================================================

This document gives the complete picture of the branch–fault feature that now spans the short‐circuit provider (`powsybl-open-sc`) and the new upstream capabilities contributed to `powsybl-open-loadflow`.  It is meant as a detailed reference both for software developers (Java/C++) and for power-system specialists who need to understand the modelling assumptions.

Context & Motivation
--------------------

* **Goal** – let SCC users declare faults on transmission branches (line/cable) at any proportional location `α ∈ [0..1]`, with optional `Rf/Xf`, and run the existing balanced + unbalanced short-circuit engines without rewriting solvers.
* **Constraint** – the “Fault” API could not be extended; we therefore route all extra data through network extensions.
* **Strategy** – reuse the load-flow / admittance infrastructure to insert *virtual nodes* at the requested location: a branch is split into two synthetic Pi-model segments and the fault is applied on the new intermediate bus.

Architecture Overview
---------------------

### 1. Contract (SCC extensions)

* `ShortCircuitFaultSpecExtension`  
  Attached to `Network`.  Stores a map `faultId → BranchFaultSpec(branchId, positionAlpha, referenceSide)`.  Created via `ShortCircuitFaultSpecExtensionAdder`.
* `BranchFaultSpec`  
  Carries the branch identifier, the proportional location `α` and the side (`FROM`/`TO`) used as a reference when interpreting α.
* Provider input  
  – SCC clients still create `BranchFault` instances (from the short-circuit API).  
  – When an extension entry exists, it overrides the `BranchFault` embedded data.  
  – If nothing is provided, the provider defaults to α = 0.5 and emits a warning.

### 2. Provider logic (`OpenShortCircuitProvider`)

1. **Resolution** – `BranchFaultSpecificationResolver` merges the `Fault` definition and the network extension, detects unsupported elements (transformers / dangling lines) and returns either a `Resolution` or a failure diagnostic.
2. **Edge-case guard** – new logic detects α ≈ 0 or α ≈ 1 (`1e-9` tolerance) and directly converts the request to a plain `BusFault`. This eliminates the 10 % deviation we noticed between branch/bus currents for IEEE cases.
3. **Context registration** – for genuine branch faults, we create a `BranchFaultContext` storing `faultId`, `branchId`, `α`, `BranchSide`.  
   The contexts are forwarded to `ShortCircuitEngineParameters`.

### 3. Engine glue (`AbstractShortCircuitEngine`)

* During construction, the engine inspects every `LfNetwork` created by the load-flow loader.
* For each context belonging to a given `LfNetwork`, we invoke the new `AdmittanceVirtualNetwork.Builder` (provided by open-loadflow) with a `BranchFaultVirtualNode`.
* The builder returns an `AdmittanceVirtualNetwork.BranchFaultVirtualNodeInstance`, i.e. the synthetic bus and (optionally) the two new Pi branches.
* Short-circuit extensions such as `ScLine` / `HomopolarModel` are copied to the synthetic segments so that balanced and unbalanced solvers see coherent data.
* We register the synthetic bus id back into `ShortCircuitEngineParameters` so that the solver knows where to inject the fault when building `ShortCircuitFault`s.

### 4. Solver / Matrix updates (`powsybl-open-loadflow` fork)

All the load-flow side changes live in the fork https://github.com/rte-i/powsybl-open-loadflow (branch `branch-fault-support`).  Key components:

* `BranchFaultVirtualNode`  
  Immutable descriptor (faultId, branchId, α, reference side).  Validates bounds and exposes helpers such as `getPortionFromBus1()`.
* `AdmittanceVirtualNetwork`  
  Wraps an `LfNetwork` and, when requested, creates synthetic buses + branches:
  * If α is ~0 or ~1, the existing terminal bus is reused (same behavior we now mirror in the provider).
  * Otherwise the original branch is temporarily removed, a virtual bus is created (voltage/angle inherited from the reference bus) and two `VirtualAdmittanceBranch` instances are created using scaled Pi models; shunts are kept on the proper ends.
  * Asymmetrical line data is also scaled so zero-sequence modelling survives.
  * Tap ratio ≠ 1.0 or phase-shifting branches are rejected explicitly (transformers are phase 2 work).
* `AdmittanceEquationSystem` / `AdmittanceMatrix`  
  The equation system now accepts an optional `AdmittanceVirtualNetwork`, and `AdmittanceMatrix` has been patched (see below) to size its bus-index accessors using the highest element number so that virtual buses no longer overflow indices.

### 5. Matrix sizing fix (`AdmittanceMatrix`)

Previously the SCC copy of `AdmittanceMatrix` allocated the accessor arrays with a length equal to `lfNetwork.getBuses().size()`.  When a virtual network added extra buses, the solver tried to stamp rows beyond the allocated arrays, triggering `IndexOutOfBoundsException`.  
The new logic scans the sorted equations to find the maximum element number and uses `max(busNum) + 1` as the accessor size (falling back to the old size if no element appears).  This keeps the matrix data structures coherent with the virtual topology.

Testing
-------

1. **OpenLoadFlow side** – the fork already contains a large suite (`BranchFaultVirtualNodeTest`, `ShortCircuitBranchFaultBalancedTest`, etc.) that checks structural and electrical equivalence (balanced/unbalanced currents, Rf/Xf impact, IEEE grids).  Those tests run as part of the load-flow build.
2. **OpenSC side** – new module dependency `powsybl-ieee-cdf-converter` lets us load the IEEE‑14 benchmark.  The newly added `ShortCircuitBranchFaultIeeeTest` performs:
   * Bus vs branch current comparison for α = 0/0.5/1 on every line.
   * Validation that transformer faults produce FAILURE + diagnostics.
   * Per-fault performance comparison (branch vs bus faults).
3. **Existing regression** – `ShortCircuitBalancedTest.branchFaultMatchesTerminalBusFaults` now benefits from the α→bus fallback introduced in the provider, so its assertions (branch fault equals terminal bus fault) remain valid.

Current Process Recap
---------------------

1. **Client input** – Provide a list of `Fault`s; for branch cases ensure that the `Network` carries `ShortCircuitFaultSpecExtension` entries so the provider knows the branch id and α.  If no extension is supplied, SCC defaults to α = 0.5 and logs a diagnostic.
2. **Provider** – `OpenShortCircuitProvider.run(...)` resolves each fault:
   * Bus faults proceed unchanged.
   * Branch faults:
     - Reject PARALLEL connection or unsupported equipment early.
     - Use the extension to identify branch + α.
     - Shortcut to bus faults when α≈0 or 1 (no virtual nodes needed).
     - Otherwise register a `BranchFaultContext` for the engines.
3. **Engines** – When instantiating `ShortCircuitBalancedEngine` / `ShortCircuitUnbalancedEngine`, the base class builds `AdmittanceVirtualNetwork`s and asks the load-flow layer to split branches accordingly.  Calculations are run as usual; `ShortCircuitFault` objects now refer to the synthetic bus IDs.
4. **Results** – `ShortCircuitAnalysisResult` behaves like before (fault currents, diagnostics, feeder contributions), but the IEEE regression gives confidence that branch faults align with bus-fault limits and that unsupported cases are clearly reported.

What Changed
------------

| Layer | Before | After |
|-------|--------|-------|
| Contract | No way to declare per-fault branch position | `ShortCircuitFaultSpecExtension` and helpers |
| Provider | Ignored `Fault.Type.BRANCH` (continued) | Resolved branch specs, created contexts, fallback to buses for α≈0/1 |
| Engine | No knowledge of branch contexts | Builds `AdmittanceVirtualNetwork`s, copies SC extensions, maps SC faults to virtual buses |
| Solver (open-loadflow) | Could not split branches dynamically | Virtual node API + admittance builder support, matrix sizing fix |
| Tests | Only internal toy networks | IEEE‑14 regression, transformer failure check, performance comparison |

How to Share This with Dev / Power Engineers
--------------------------------------------

* **Software developers** – focus on the class-level notes above (`BranchFaultSpecificationResolver`, `OpenShortCircuitProvider`, `AdmittanceVirtualNetwork`, `AdmittanceMatrix`).  Running `./mvnw -pl sc-implementation -Dtest=ShortCircuitBranchFaultIeeeTest test` gives deterministic evidence that the flow works end-to-end.
* **Power-system specialists** – the branch fault is modelled as two series segments with untouched shunts; mid-span current is bounded by the terminal Thevenin equivalents; transformer faults still fail (phase 2).  Use the IEEE test output as a reference table for expected currents when validating SC studies.

Next Steps
----------

* Extend the virtual-node builder to support tapped / phase-shifted transformers so that branch faults on TFOs become feasible.
* Surface study-report diagnostics for “fault defaulted to α=0.5” or “branch unavailable” directly in the UI.
* Consider adding CLI/GUI tooling to populate `ShortCircuitFaultSpecExtension` entries (currently done programmatically in tests).
