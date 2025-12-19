Study Options & Provider Pipeline Improvements
=============================================

This note summarises the earlier tickets we delivered before the branch-fault epic.  The focus is on how short-circuit study options are expressed, how the provider reacts to them, and how fault/feeder results are handled end-to-end.  The information below targets both software developers and power-system engineers who need to understand the new behaviours.

1. Study Options Extension
--------------------------

### 1.1 Contract

* **Location** – `sc-extensions/src/main/java/com/powsybl/sc/extensions/ShortCircuitStudyOptionsExtension.java`
* **Purpose** – attach the choice of norm, analysis period, and voltage profile to an IIDM `Network`.
* **Fields**  
  `Norm` = `IEC_60909` or `NONE`  
  `Period` = `SUB_TRANSIENT`, `TRANSIENT`, `STEADY_STATE`  
  `VoltageProfile` = `NOMINAL` or `CALCULATED`
* **Adder / Provider** – `ShortCircuitStudyOptionsExtensionAdder` + `ShortCircuitStudyOptionsExtensionAdderImplProvider` allow CLI/UI layers to populate the extension with a single fluent API call.

### 1.2 Usage

* `OpenShortCircuitProvider` reads the extension at the very beginning of `run(...)`.  If the extension is absent, we default to IEC‑60909, sub-transient, and nominal profile so existing clients keep their behaviour.
* Every `ShortCircuitAnalysisResult` receives a `ShortCircuitStudyReport` (another extension) capturing the norm/period/voltage profile plus any diagnostics raised while processing faults.  Tests such as `ShortCircuitBalancedTest` and `ShortCircuitIecProviderTest` assert that the report is attached and contains the requested parameters.

2. Voltage Profile Selection & Load-Flow Coupling
-------------------------------------------------

### 2.1 Calculated profile

When `ShortCircuitStudyOptionsExtension.VoltageProfile` is `CALCULATED`, the provider now:

1. Instantiates an `OpenLoadFlowProvider` (same matrix factory as the short-circuit engine) and runs a load-flow using `LoadFlow.Runner`.
2. Passes the resulting `LoadFlowParameters` and the `useCalculatedVoltageProfile` flag to `ShortCircuitEngineParameters`.
3. The balanced/unbalanced engines translate this to `AdmittanceEquationSystem.AdmittanceVoltageProfileType.CALCULATED`, so the admittance matrix is built with the solved voltages rather than nominal values.

If the profile is `NOMINAL`, the provider skips the load-flow step and the shunt voltage magnitudes remain the nominal values (behaviour identical to the original release).

### 2.2 Interaction with OpenLoadFlow

We still rely on the standard open-loadflow AC solver (Newton-Raphson with distributed slack).  No changes were required upstream, but the new wiring ensures the SCC engines can request voltages that reflect the steady-state operating point rather than nominal nameplates, which is necessary for utilities that compute short-circuit currents on realistic pre-fault conditions.

3. Fault Processing & Diagnostics
---------------------------------

### 3.1 Pipeline

* `OpenShortCircuitProvider` now maintains a `FaultProcessingResult` per input `Fault`.  Each result stores:
  * The original `Fault`.
  * Either the derived `ShortCircuitFault` (ready status) or an explanatory diagnostic (failure status).
* All READY faults are converted to short-circuit faults and mapped to the original `Fault` via `scFaultToFault`.
* FAILURE results are converted to `MagnitudeFaultResult` entries with status `FAILURE`.  A `FaultProcessingDiagnostic` extension is attached so consumers (UI/tests) can display the message.
* The aggregated diagnostics are also pushed into the `ShortCircuitStudyReport` mentioned above.

### 3.2 Behavioural changes

* Unsupported connection type `PARALLEL` now yields an explicit failure instead of being silently ignored (see `ShortCircuitBalancedTest.unsupportedFaultProducesFailureResult`).
* Unknown buses or missing network elements produce deterministic diagnostics.
* Tests `ShortCircuitBalancedTest` and `ShortCircuitMonophasedTest` validate that the failure results and study report diagnostics are exposed to users.

4. Feeder Results Guard
-----------------------

The legacy provider assumed `ShortCircuitResult.getFeedersAtBusResultsDirect()` always returned data, which is not true when the engine is configured without feeder contributions.  To avoid `NullPointerException`s, `fillFeederResults(...)` now checks for `null` or empty maps before iterating.  When feeders are available, they are converted to `MagnitudeFeederResult` objects (normalised to the same unit as Ik3).  When feeders are absent, we simply return an empty list and continue; this matches Transnet’s requirement to tolerate engines that do not compute feeder contributions.

5. Current Process vs Previous Behaviour
----------------------------------------

| Step | Before | After |
|------|--------|-------|
| Study options | Hard-coded IEC, sub-transient, nominal | Configurable via network extension; reported in results |
| Voltage profile | Always nominal | Optional load-flow execution + calculated profile propagated to engines |
| Fault handling | Null checks ad hoc, failures often silent | Structured `FaultProcessingResult`, diagnostics, study report |
| Feeder handling | Assumed feeders always present | Optional, gracefully handles `null` |

This foundation allowed us to tackle the branch-fault epic described in `BRANCH_FAULT_SUPPORT.md`: the provider/engine pipeline is now extension-driven, resilient to missing data, and already couples with OpenLoadFlow when needed.
