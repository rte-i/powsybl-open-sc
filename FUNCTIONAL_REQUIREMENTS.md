# Functional Requirements Document (FRD)
## PowSyBl Open Short-Circuit

**Version:** 1.0  
**Date:** December 16, 2025  
**Project:** PowSyBl Open Short-Circuit - IEC 60909 Short-Circuit Analysis Implementation

---

## 1. BUSINESS REQUIREMENTS

### 1.1 Purpose
Provide power system engineers with an open-source tool to perform short-circuit fault analysis on electrical networks according to IEC 60909 standard.

### 1.2 User Needs

**Primary Users:** Power system engineers, grid operators, simulation specialists

**Core Capabilities:**
1. **Load and analyze power networks** - Import CGMES, IIDM, and other network formats
2. **Configure fault scenarios** - Define bus faults, branch faults, and fault parameters
3. **Run short-circuit calculations** - Execute balanced (3-phase) and unbalanced (1-phase, 2-phase) fault analysis
4. **Apply industry standards** - Use IEC 60909 voltage factors (Cmax/Cmin) for compliance
5. **Control analysis parameters** - Configure study type (sub-transient/transient/steady-state), voltage profiles, and calculation options
6. **View comprehensive results** - Access fault currents, voltages, feeder contributions, Fortescue components, and limit violations
7. **Validate against limits** - Check short-circuit currents against equipment ratings
8. **Analyze network impact** - Assess voltage drops and system-wide fault effects

---

## 2. GAP ANALYSIS

### 2.1 Current State vs. Desired State

#### ✅ WORKING FEATURES

| Feature | Status | Notes |
|---------|--------|-------|
| Three-phase balanced faults | ✅ Complete | TRIPHASED_GROUND supported |
| Unbalanced faults | ✅ Complete | MONOPHASED, BIPHASED, BIPHASED_GROUND |
| Thévenin equivalent calculation | ✅ Complete | Impedance and voltage calculation |
| Feeder contribution calculation | ✅ Complete | Branch current contributions |
| IEC 60909 voltage factors | ⚠️ Implemented | But not used by default (Bug #1) |
| CGMES import | ✅ Complete | Post-processor available |
| Generator/Load extensions | ✅ Complete | ShortCircuit extensions available |
| SUB_TRANSIENT/TRANSIENT periods | ✅ Complete | Period selection working |

#### ❌ BROKEN/MISSING FEATURES

| Feature | Issue | Priority | Impact |
|---------|-------|----------|--------|
| **Default norm is NONE** | Line 93: `new ShortCircuitNormNone()` | 🔴 CRITICAL | Wrong voltage factors (1.0 instead of IEC) |
| **Short-circuit power = 0** | Line 128: Hardcoded `0.` | 🔴 CRITICAL | Essential result missing |
| **API parameters ignored** | Lines 86-95: Hardcoded values | 🔴 CRITICAL | User configuration broken |
| **BranchFault not supported** | Line 191: "not yet supported" | 🟠 HIGH | Cannot model line faults |
| **with-voltage-result** | Not exposed | 🟠 HIGH | No voltage profile results |
| **with-feeder-result** | Hardcoded true | 🟡 MEDIUM | Not configurable |
| **with-limit-violations** | Not implemented | 🟠 HIGH | No equipment validation |
| **with-fortescue-result** | Not implemented | 🟠 HIGH | No sequence components |
| **FaultParameters** | Not used | 🟡 MEDIUM | Per-fault config impossible |
| **Peak current (ip)** | Not calculated | 🟡 MEDIUM | IEC 60909 requirement |

### 2.2 Technical Debt

**Architecture Issues:**
- Internal `ShortCircuitEngineParameters` does not map to PowSyBl API `ShortCircuitParameters`
- No adapter/mapper between API and engine parameters
- Results limited to `MagnitudeFaultResult` - missing `FortescueFaultResult`, `ShortCircuitBusResult`
- Incomplete `FaultResult` implementation (missing voltage, timeConstant, status codes)

**Critical Code References:**
- [OpenShortCircuitProvider.java:93](sc-implementation/src/main/java/com/powsybl/sc/implementation/OpenShortCircuitProvider.java#L93) - Wrong default norm
- [OpenShortCircuitProvider.java:128](sc-implementation/src/main/java/com/powsybl/sc/implementation/OpenShortCircuitProvider.java#L128) - Zero short-circuit power
- [OpenShortCircuitProvider.java:86-95](sc-implementation/src/main/java/com/powsybl/sc/implementation/OpenShortCircuitProvider.java#L86-L95) - Hardcoded parameters

---

## 3. FUNCTIONAL REQUIREMENTS DOCUMENT

### Epic 1: Core API Parameter Support
**Priority:** 🔴 CRITICAL  
**Goal:** Enable users to configure short-circuit calculations via the official PowSyBl API

#### User Story 1.1: Fix Default Norm
**As a** power system engineer  
**I want** IEC 60909 to be the default norm  
**So that** calculations use correct voltage factors without manual configuration

**Acceptance Criteria:**
- Default `ShortCircuitNorm` is IEC 60909, not NONE
- Cmax/Cmin voltage factors applied according to IEC standard
- Users can still override with custom norms

**Tasks:**
- [ ] **Task 1.1.1:** Replace `new ShortCircuitNormNone()` with `new ShortCircuitNormIec()` in [OpenShortCircuitProvider.java:93](sc-implementation/src/main/java/com/powsybl/sc/implementation/OpenShortCircuitProvider.java#L93)
- [ ] **Task 1.1.2:** Add parameter to allow norm selection via API
- [ ] **Task 1.1.3:** Update unit tests to verify IEC norm is default
- [ ] **Task 1.1.4:** Document norm selection in user guide

---

#### User Story 1.2: Calculate Short-Circuit Power
**As a** power system engineer  
**I want** the actual short-circuit power (MVA) in results  
**So that** I can assess fault severity and equipment requirements

**Acceptance Criteria:**
- `shortCircuitPower` field contains calculated MVA value
- Formula: $S_{sc} = \sqrt{3} \cdot V_n \cdot I_k$ (kA, kV, MVA)
- Value matches manual calculations

**Tasks:**
- [ ] **Task 1.2.1:** Implement short-circuit power calculation in [OpenShortCircuitProvider.java:128](sc-implementation/src/main/java/com/powsybl/sc/implementation/OpenShortCircuitProvider.java#L128)
- [ ] **Task 1.2.2:** Use formula from [ShortCircuitResult.java:370](sc-implementation/src/main/java/com/powsybl/sc/implementation/ShortCircuitResult.java#L370) (`getPcc()` method)
- [ ] **Task 1.2.3:** Add unit test validating power calculation
- [ ] **Task 1.2.4:** Verify against IEC 60909 example cases

---

#### User Story 1.3: Support API Parameters
**As a** power system engineer  
**I want** to configure calculations via `ShortCircuitParameters`  
**So that** I can control analysis behavior from my application

**Acceptance Criteria:**
- All parameters from API specification are read and applied
- Internal `ShortCircuitEngineParameters` is populated from API parameters
- Hardcoded values in lines 86-95 are removed

**Tasks:**
- [ ] **Task 1.3.1:** Create `ParameterMapper` class to convert `ShortCircuitParameters` → `ShortCircuitEngineParameters`
- [ ] **Task 1.3.2:** Map voltage profile modes (NOMINAL/PREVIOUS/CONFIGURED)
- [ ] **Task 1.3.3:** Map study type (SUB_TRANSIENT/TRANSIENT/STEADY_STATE)
- [ ] **Task 1.3.4:** Map boolean flags (with-loads, with-shunt-compensators, etc.)
- [ ] **Task 1.3.5:** Replace hardcoded values in [OpenShortCircuitProvider.java:86-95](sc-implementation/src/main/java/com/powsybl/sc/implementation/OpenShortCircuitProvider.java#L86-95)
- [ ] **Task 1.3.6:** Add integration test verifying parameter propagation

**Files to modify:**
- `sc-implementation/src/main/java/com/powsybl/sc/implementation/OpenShortCircuitProvider.java`
- New file: `sc-implementation/src/main/java/com/powsybl/sc/util/ParameterMapper.java`

---

### Epic 2: IEC 60909 Compliance
**Priority:** 🟠 HIGH  
**Goal:** Complete implementation of IEC 60909 standard calculations

#### User Story 2.1: Peak Current Calculation
**As a** power system engineer  
**I want** peak short-circuit current ($i_p$) in results  
**So that** I can specify circuit breaker ratings

**Acceptance Criteria:**
- Calculate peak current using IEC 60909 formula: $i_p = \kappa \cdot \sqrt{2} \cdot I_k$
- Return value in `FaultResult`
- Factor κ based on R/X ratio

**Tasks:**
- [ ] **Task 2.1.1:** Implement peak current calculation in `ShortCircuitResult`
- [ ] **Task 2.1.2:** Calculate κ factor from R/X ratio
- [ ] **Task 2.1.3:** Add `peakCurrent` field to `MagnitudeFaultResult`
- [ ] **Task 2.1.4:** Validate against IEC 60909 examples

---

#### User Story 2.2: Time Constants
**As a** power system engineer  
**I want** fault current decay time constants  
**So that** I can analyze transient behavior

**Acceptance Criteria:**
- Calculate DC time constant $T_a$ and AC time constant $T_{sym}$
- Return as `Duration` in `FaultResult`

**Tasks:**
- [ ] **Task 2.2.1:** Extract time constants from generator parameters
- [ ] **Task 2.2.2:** Calculate system equivalent time constant
- [ ] **Task 2.2.3:** Add `timeConstant` field to result objects

---

#### User Story 2.3: Voltage Ranges Configuration
**As a** power system engineer  
**I want** to specify custom voltage coefficients per voltage level  
**So that** I can use site-specific or regional variations of IEC 60909

**Acceptance Criteria:**
- Support `initial-voltage-profile-mode` = CONFIGURED
- Read voltage ranges from JSON configuration
- Apply Cmax/Cmin per voltage level

**Tasks:**
- [ ] **Task 2.3.1:** Design voltage-ranges JSON schema
- [ ] **Task 2.3.2:** Implement JSON parser for voltage ranges
- [ ] **Task 2.3.3:** Apply custom ranges in `ShortCircuitNormIec`
- [ ] **Task 2.3.4:** Add example configuration files

---

### Epic 3: Complete Result Objects
**Priority:** 🟠 HIGH  
**Goal:** Provide comprehensive fault analysis results to users

#### User Story 3.1: Voltage Profile Results
**As a** power system engineer  
**I want** voltage at all network buses during fault  
**So that** I can analyze voltage drops and sags

**Acceptance Criteria:**
- Implement `with-voltage-result` parameter
- Return `ShortCircuitBusResult` list with bus voltages
- Include voltage magnitude and angle
- Filter by `min-voltage-drop-proportional-threshold`

**Tasks:**
- [ ] **Task 3.1.1:** Create `ShortCircuitBusResult` class
- [ ] **Task 3.1.2:** Extract voltage data from `ShortCircuitResult.busNum2Dv`
- [ ] **Task 3.1.3:** Implement voltage drop threshold filtering
- [ ] **Task 3.1.4:** Add to `MagnitudeFaultResult` as optional field
- [ ] **Task 3.1.5:** Update [OpenShortCircuitProvider.java:128](sc-implementation/src/main/java/com/powsybl/sc/implementation/OpenShortCircuitProvider.java#L128) to include bus results when requested

**Files to create:**
- `sc-implementation/src/main/java/com/powsybl/sc/util/ShortCircuitBusResult.java`

---

#### User Story 3.2: Fortescue Sequence Components
**As a** power system engineer  
**I want** positive, negative, and zero sequence currents/voltages  
**So that** I can analyze unbalanced faults accurately

**Acceptance Criteria:**
- Implement `with-fortescue-result` parameter
- Return `FortescueFaultResult` for unbalanced faults
- Include magnitude and angle for each sequence (0, +, -)
- Provide `FortescueFeederResult` per branch

**Tasks:**
- [ ] **Task 3.2.1:** Create `FortescueFaultResult` class extending `FaultResult`
- [ ] **Task 3.2.2:** Create `FortescueFeederResult` class
- [ ] **Task 3.2.3:** Create `FortescueShortCircuitBusResult` class
- [ ] **Task 3.2.4:** Extract Fortescue data from [ShortCircuitResult.java:217](sc-implementation/src/main/java/com/powsybl/sc/implementation/ShortCircuitResult.java#L217) (`vFortescue`)
- [ ] **Task 3.2.5:** Convert from Fortescue matrix to result objects
- [ ] **Task 3.2.6:** Return appropriate result type in `runUnbalancedAnalysis()`

**Files to create:**
- `sc-implementation/src/main/java/com/powsybl/sc/util/FortescueFaultResult.java`
- `sc-implementation/src/main/java/com/powsybl/sc/util/FortescueFeederResult.java`
- `sc-implementation/src/main/java/com/powsybl/sc/util/FortescueShortCircuitBusResult.java`

---

#### User Story 3.3: Limit Violations
**As a** power system engineer  
**I want** automatic detection of short-circuit current limit violations  
**So that** I can identify equipment at risk

**Acceptance Criteria:**
- Implement `with-limit-violations` parameter
- Check currents against `LimitType.LOW_SHORT_CIRCUIT_CURRENT` and `HIGH_SHORT_CIRCUIT_CURRENT`
- Return list of violated limits with details
- Include bus/branch ID, limit value, actual value

**Tasks:**
- [ ] **Task 3.3.1:** Query network for short-circuit current limits
- [ ] **Task 3.3.2:** Compare calculated currents vs. limits
- [ ] **Task 3.3.3:** Create `LimitViolation` objects
- [ ] **Task 3.3.4:** Populate `limitViolations` list in [OpenShortCircuitProvider.java:128](sc-implementation/src/main/java/com/powsybl/sc/implementation/OpenShortCircuitProvider.java#L128)
- [ ] **Task 3.3.5:** Add unit test with limit-violation scenarios

---

#### User Story 3.4: Fault Status Codes
**As a** power system engineer  
**I want** clear status information for each fault result  
**So that** I can distinguish successful calculations from failures

**Acceptance Criteria:**
- Support all status codes: SUCCESS, NO_SHORT_CIRCUIT_DATA, SOLVER_FAILURE, FAILURE
- Set appropriate status based on calculation outcome
- Log details of failures

**Tasks:**
- [ ] **Task 3.4.1:** Add error handling in `runBalancedAnalysis()` and `runUnbalancedAnalysis()`
- [ ] **Task 3.4.2:** Detect missing extension data → NO_SHORT_CIRCUIT_DATA
- [ ] **Task 3.4.3:** Catch solver exceptions → SOLVER_FAILURE
- [ ] **Task 3.4.4:** Set status in `FaultResult` constructor
- [ ] **Task 3.4.5:** Add logging for non-SUCCESS statuses

**Files to modify:**
- [OpenShortCircuitProvider.java:132-160](sc-implementation/src/main/java/com/powsybl/sc/implementation/OpenShortCircuitProvider.java#L132-L160)

---

### Epic 4: Fault Type Support
**Priority:** 🟠 HIGH  
**Goal:** Support all fault types specified in PowSyBl API

#### User Story 4.1: Branch Faults
**As a** power system engineer  
**I want** to model faults on transmission lines/cables  
**So that** I can analyze line fault scenarios

**Acceptance Criteria:**
- Support `BranchFault` input type
- Implement `proportionalLocation` (0.0 = from side, 1.0 = to side)
- Calculate fault at specified location along line
- Handle line impedance splitting

**Tasks:**
- [ ] **Task 4.1.1:** Modify fault list builder to accept BranchFault
- [ ] **Task 4.1.2:** Implement line impedance splitting at fault location
- [ ] **Task 4.1.3:** Create internal representation for branch faults
- [ ] **Task 4.1.4:** Update error message at line 191 (remove "not yet supported")
- [ ] **Task 4.1.5:** Add branch fault test cases

**Files to modify:**
- `sc-implementation/src/main/java/com/powsybl/sc/implementation/AbstractShortCircuitEngine.java` (line 191)
- Fault conversion logic in `OpenShortCircuitProvider`

---

#### User Story 4.2: Parallel Connection Faults
**As a** power system engineer  
**I want** to model faults with parallel connection type  
**So that** I can analyze shunt faults

**Acceptance Criteria:**
- Support `ConnectionType.PARALLEL`
- Model fault impedance in parallel with network

**Tasks:**
- [ ] **Task 4.2.1:** Implement parallel fault connection logic
- [ ] **Task 4.2.2:** Update admittance matrix for parallel faults
- [ ] **Task 4.2.3:** Remove error at line 196 (remove "not yet supported")
- [ ] **Task 4.2.4:** Add parallel connection test cases

---

### Epic 5: Advanced Features
**Priority:** 🟡 MEDIUM  
**Goal:** Enhance analysis capabilities and configurability

#### User Story 5.1: Systematic Analysis
**As a** power system engineer  
**I want** to automatically calculate faults at all network buses  
**So that** I can find weakest points in the system

**Acceptance Criteria:**
- Systematic analysis mode fully functional
- Option to select all buses or filtered subset
- Efficient execution for large networks

**Tasks:**
- [ ] **Task 5.1.1:** Verify systematic mode works for balanced faults
- [ ] **Task 5.1.2:** Implement systematic mode for unbalanced faults
- [ ] **Task 5.1.3:** Add progress reporting for large analyses
- [ ] **Task 5.1.4:** Optimize performance (parallel execution)

---

#### User Story 5.2: Per-Fault Parameters
**As a** power system engineer  
**I want** to override parameters for specific faults  
**So that** I can model different scenarios in one run

**Acceptance Criteria:**
- Use `FaultParameters` list from API
- Override global parameters per fault
- Support all configurable parameters

**Tasks:**
- [ ] **Task 5.2.1:** Process `faultParameters` list in `run()` method
- [ ] **Task 5.2.2:** Create parameter resolver (global → per-fault override)
- [ ] **Task 5.2.3:** Apply per-fault parameters in engine
- [ ] **Task 5.2.4:** Add test with mixed fault parameters

**Files to modify:**
- [OpenShortCircuitProvider.java:62](sc-implementation/src/main/java/com/powsybl/sc/implementation/OpenShortCircuitProvider.java#L62) (`faultParameters` currently unused)

---

#### User Story 5.3: Additional Parameters
**As a** power system engineer  
**I want** to configure all parameters from the API specification  
**So that** I have full control over calculations

**Acceptance Criteria:**
- Support all missing parameters from Gap Analysis section 2.1
- Parameters: with-loads, with-shunt-compensators, with-vsc-converter-stations, with-neutral-position, sub-transient-coefficient

**Tasks:**
- [ ] **Task 5.3.1:** Implement `with-loads` parameter (include/exclude loads)
- [ ] **Task 5.3.2:** Expose `with-shunt-compensators` (map from internal `ignoreShunts`)
- [ ] **Task 5.3.3:** Implement `with-vsc-converter-stations`
- [ ] **Task 5.3.4:** Implement `with-neutral-position` (neutral grounding)
- [ ] **Task 5.3.5:** Implement `sub-transient-coefficient` multiplier
- [ ] **Task 5.3.6:** Add test coverage for each parameter

---

#### User Story 5.4: Battery Extension
**As a** power system engineer  
**I want** battery short-circuit contribution modeling  
**So that** I can analyze grids with energy storage systems

**Acceptance Criteria:**
- Create `BatteryShortCircuit` extension
- Model battery impedance and contribution
- Include in short-circuit calculations

**Tasks:**
- [ ] **Task 5.4.1:** Design `BatteryShortCircuit` extension (similar to `GeneratorShortCircuit2`)
- [ ] **Task 5.4.2:** Create extension classes in `sc-extensions/`
- [ ] **Task 5.4.3:** Add battery handling in calculation engines
- [ ] **Task 5.4.4:** Document battery extension parameters

**Files to create:**
- `sc-extensions/src/main/java/com/powsybl/sc/extensions/BatteryShortCircuit.java`
- `sc-extensions/src/main/java/com/powsybl/sc/extensions/BatteryShortCircuitAdder.java`

---

#### User Story 5.5: Enhanced Reporting
**As a** power system engineer  
**I want** detailed calculation reports and debugging output  
**So that** I can verify results and troubleshoot issues

**Acceptance Criteria:**
- Implement `detailedReport` parameter
- Implement `debugDir` parameter
- Export intermediate calculation data

**Tasks:**
- [ ] **Task 5.5.1:** Create report generator with calculation details
- [ ] **Task 5.5.2:** Export debug files (admittance matrices, voltage vectors)
- [ ] **Task 5.5.3:** Generate HTML/PDF reports
- [ ] **Task 5.5.4:** Add logging configuration options

---

## 4. IMPLEMENTATION PRIORITY

### Phase 1: Critical Fixes (Immediate - 2 weeks)
- Epic 1: Core API Parameter Support (all stories)
  - Fix default norm ✅
  - Calculate short-circuit power ✅
  - Support API parameters ✅

### Phase 2: Core Features (1-2 months)
- Epic 3: Complete Result Objects (Stories 3.1, 3.3, 3.4)
  - Voltage profile results
  - Limit violations
  - Status codes
- Epic 4: Fault Type Support (Story 4.1)
  - Branch faults

### Phase 3: Advanced Analysis (2-4 months)
- Epic 2: IEC 60909 Compliance (all stories)
  - Peak current
  - Time constants
  - Voltage ranges
- Epic 3: Complete Result Objects (Story 3.2)
  - Fortescue components

### Phase 4: Enhanced Capabilities (4-6 months)
- Epic 4: Fault Type Support (Story 4.2)
  - Parallel connections
- Epic 5: Advanced Features (all stories)
  - Systematic analysis
  - Per-fault parameters
  - Additional parameters
  - Battery extension
  - Enhanced reporting

---

## 5. ACCEPTANCE TESTING

### Test Scenarios

**TS-1: IEC 60909 Compliance**
- Given: Standard IEC 60909 test network
- When: Running short-circuit analysis
- Then: Results match IEC examples within 1% tolerance

**TS-2: Parameter Propagation**
- Given: API parameters with custom values
- When: Executing analysis
- Then: Internal engine uses specified parameters

**TS-3: Branch Fault Calculation**
- Given: 400kV line with fault at 50% location
- When: Running BranchFault analysis
- Then: Fault current calculated at midpoint impedance

**TS-4: Voltage Drop Analysis**
- Given: Network with voltage-result enabled
- When: Fault occurs at bus
- Then: All bus voltages returned with correct drops

**TS-5: Limit Violation Detection**
- Given: Network with 50kA breaker limit
- When: Fault produces 60kA current
- Then: Violation reported with details

---

## 6. NON-FUNCTIONAL REQUIREMENTS

### Performance
- Process networks with 10,000+ buses
- Systematic analysis completes in reasonable time (<1 hour for 1000 buses)
- Memory usage scales linearly with network size

### Reliability
- Handle missing extension data gracefully
- Validate input parameters
- Provide meaningful error messages
- No crashes on invalid input

### Compatibility
- Java 11+ compatible
- Maven build system
- PowSyBl Core API compliance
- Backward compatible with existing extensions

### Maintainability
- Well-documented code
- Unit test coverage >80%
- Integration tests for key scenarios
- Clear separation of concerns (API ↔ Engine ↔ Solver)

---

## 7. REFERENCES

- [PowSyBl Short-Circuit API Documentation](https://powsybl.readthedocs.io/projects/powsybl-core/en/stable/simulation/shortcircuit/)
- [IEC 60909 Standard](https://webstore.iec.ch/publication/3646)
- [Gap Analysis Document](GAP_ANALYSIS.md)
- [Project README](README.md)

---

## 8. GLOSSARY

| Term | Definition |
|------|------------|
| **IEC 60909** | International standard for short-circuit current calculation |
| **Cmax/Cmin** | Voltage factors for maximum/minimum short-circuit current |
| **Thévenin Equivalent** | Simplified network model (Zth, Eth) at fault point |
| **Fortescue Components** | Symmetrical components (positive, negative, zero sequence) |
| **$I_k$** | Initial symmetrical short-circuit current |
| **$i_p$** | Peak short-circuit current |
| **Feeder** | Network branch contributing current to fault |
| **BusFault** | Fault at network bus/node |
| **BranchFault** | Fault on transmission line/cable |
| **SUB_TRANSIENT** | First cycles after fault (using X"d) |
| **TRANSIENT** | Few cycles after fault (using X'd) |
| **STEADY_STATE** | Sustained fault condition |

---

**Document Status:** DRAFT v1.0  
**Next Review:** After Phase 1 completion  
**Approval Required:** Technical Lead, Product Owner
