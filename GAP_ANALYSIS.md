# Gap Analysis: PowSyBl Open Short-Circuit vs Official API

## Summary
This document compares the current implementation in `powsybl-open-sc` with the official PowSyBl Short-Circuit API specification from [powsybl-core documentation](https://powsybl.readthedocs.io/projects/powsybl-core/en/stable/simulation/shortcircuit/).

---

## ✅ IMPLEMENTED Features

### Core Functionality
- ✅ Three-phase balanced fault calculation (TRIPHASED_GROUND)
- ✅ Single-phase unbalanced fault calculation (MONOPHASED) 
- ✅ Biphasic fault calculation (BIPHASED, BIPHASED_GROUND)
- ✅ IEC 60909 norm support with Cmax/Cmin voltage factors
- ✅ Thévenin equivalent impedance calculation
- ✅ Feeder contribution results
- ✅ Matrix-based admittance system
- ✅ Selective analysis (specific faults)
- ✅ Systematic analysis (all buses)
- ✅ SUB_TRANSIENT and TRANSIENT period support

### Extensions
- ✅ GeneratorShortCircuit2 extension with transient/subtransient parameters
- ✅ GeneratorFortescueType for sequence components
- ✅ LoadShortCircuit extension
- ✅ CGMES import post-processor

---

## ❌ MISSING Features (from API specification)

### 1. **API Parameters - NOT Implemented**

According to the [Parameters documentation](https://powsybl.readthedocs.io/projects/powsybl-core/en/stable/simulation/shortcircuit/parameters.html), the following parameters are **NOT supported**:

| Parameter | API Spec | Implementation Status | Priority |
|-----------|----------|----------------------|----------|
| `with-voltage-result` | ✅ Specified | ❌ Not exposed | **HIGH** |
| `with-feeder-result` | ✅ Specified | ⚠️ Partially (hardcoded true) | **MEDIUM** |
| `with-limit-violations` | ✅ Specified | ❌ Not implemented | **HIGH** |
| `study-type` | ✅ Specified (SUB_TRANSIENT/TRANSIENT/STEADY_STATE) | ⚠️ Partial (STEADY_STATE missing) | **MEDIUM** |
| `with-fortescue-result` | ✅ Specified | ❌ Not implemented | **HIGH** |
| `min-voltage-drop-proportional-threshold` | ✅ Specified | ❌ Not implemented | **MEDIUM** |
| `with-loads` | ✅ Specified | ❌ Not configurable | **MEDIUM** |
| `with-shunt-compensators` | ✅ Specified | ⚠️ Exists as `ignoreShunts` but not in API | **MEDIUM** |
| `with-vsc-converter-stations` | ✅ Specified | ❌ Not implemented | **LOW** |
| `with-neutral-position` | ✅ Specified | ❌ Not implemented | **MEDIUM** |
| `initial-voltage-profile-mode` | ✅ Specified (NOMINAL/PREVIOUS/CONFIGURED) | ⚠️ Exists as internal `VoltageProfileType` but only NOMINAL/CALCULATED | **HIGH** |
| `voltage-ranges` | ✅ Specified | ❌ Not implemented | **HIGH** |
| `sub-transient-coefficient` | ✅ Specified | ❌ Not implemented | **MEDIUM** |
| `detailedReport` | ✅ Specified | ❌ Not implemented | **LOW** |
| `debugDir` | ✅ Specified | ❌ Not implemented | **LOW** |

**Current Implementation:**
- Uses internal `ShortCircuitEngineParameters` class
- Does **NOT** map to official `ShortCircuitParameters` from API
- Parameters are hardcoded in [OpenShortCircuitProvider.java:86-95](sc-implementation/src/main/java/com/powsybl/sc/implementation/OpenShortCircuitProvider.java#L86-L95)

### 2. **Fault Input Types - Partially Missing**

According to [Inputs documentation](https://powsybl.readthedocs.io/projects/powsybl-core/en/stable/simulation/shortcircuit/inputs.html):

| Fault Type | API Spec | Implementation | Issue |
|------------|----------|----------------|-------|
| `BusFault` | ✅ Required | ✅ Supported | - |
| `BranchFault` | ✅ Required | ❌ **NOT SUPPORTED** | See line 191: "BRANCH not yet supported" |
| `proportionalLocation` | ✅ For BranchFault | ❌ Not implemented | Required for line faults |
| `ConnectionType.PARALLEL` | ✅ Specified | ❌ **NOT SUPPORTED** | See line 196: "PARALLEL not yet supported" |
| `ConnectionType.SERIES` | ✅ Specified | ✅ Supported | - |

**Limitations:**
- Line 191: `"Short circuit of type BRANCH not yet supported, fault: {} is ignored"`
- Line 196: `"Short circuit connection of type PARALLEL not yet supported, fault: {} is ignored"`

### 3. **Output Results - Significantly Incomplete**

According to [Outputs documentation](https://powsybl.readthedocs.io/projects/powsybl-core/en/stable/simulation/shortcircuit/outputs.html):

#### Missing from `FaultResult`:

| Field | API Requirement | Current Implementation |
|-------|-----------------|----------------------|
| `shortCircuitPower` | ✅ Required (MVA) | ❌ Returns 0 (hardcoded line 128) |
| `timeConstant` | ✅ Required (Duration) | ❌ Not implemented |
| `voltage` | ✅ Required | ❌ Not implemented |
| `status` | ✅ Required (SUCCESS/NO_SHORT_CIRCUIT_DATA/SOLVER_FAILURE/FAILURE) | ⚠️ Only SUCCESS implemented |
| `limitViolations` | ⚠️ Optional | ❌ Empty list (line 152) |
| `shortCircuitBusResults` | ⚠️ Optional | ❌ Not implemented |

#### Missing Result Classes:

| Class | API Requirement | Implementation Status |
|-------|-----------------|----------------------|
| `FortescueFaultResult` | ✅ When with-fortescue-result=true | ❌ Not implemented |
| `FortescueFeederResult` | ✅ When with-fortescue-result=true | ❌ Not implemented |
| `FortescueShortCircuitBusResult` | ✅ When with-voltage-result=true | ❌ Not implemented |
| `ShortCircuitBusResult` | ✅ When with-voltage-result=true | ❌ Not implemented |

**Current Implementation:**
- Only returns `MagnitudeFaultResult` with current magnitude
- Short-circuit power hardcoded to 0 (line 128)
- No voltage profile results
- No Fortescue (sequence component) results
- No voltage drop analysis

### 4. **FaultParameters - Not Implemented**

According to the API, `FaultParameters` should allow per-fault parameter override:
- ❌ Not implemented
- ❌ Cannot override parameters for specific faults
- All faults use same global parameters

### 5. **Battery Short-Circuit Extension**

The API mentions:
- "batteries are stored in the short-circuit battery extension"
- ❌ No battery extension found in `sc-extensions/`
- Only Generator and Load extensions exist

---

## 🔧 IMPLEMENTATION GAPS

### Architecture Issues

1. **Parameters Mapping**
   - **Issue**: Internal `ShortCircuitEngineParameters` doesn't map to API `ShortCircuitParameters`
   - **File**: [OpenShortCircuitProvider.java:86-95](sc-implementation/src/main/java/com/powsybl/sc/implementation/OpenShortCircuitProvider.java#L86-L95)
   - **Impact**: Users cannot configure calculations via API parameters

2. **Hardcoded Configuration**
   ```java
   // Lines 86-95 in OpenShortCircuitProvider.java
   ShortCircuitEngineParameters.VoltageProfileType voltageProfile = 
       ShortCircuitEngineParameters.VoltageProfileType.NOMINAL; // HARDCODED
   
   ShortCircuitEngineParameters.AnalysisType at = 
       ShortCircuitEngineParameters.AnalysisType.SELECTIVE; // HARDCODED
   
   ShortCircuitEngineParameters.PeriodType periodType = 
       ShortCircuitEngineParameters.PeriodType.SUB_TRANSIENT; // HARDCODED
   
   ShortCircuitNorm shortCircuitNorm = new ShortCircuitNormNone(); // NOT IEC!
   ```

3. **Incomplete Results**
   ```java
   // Line 128 - Short-circuit power always 0
   MagnitudeFaultResult magnitudeFaultResult = new MagnitudeFaultResult(
       fault, 
       0.,  // ← WRONG: Should calculate S = √3 * U * I
       feederResults, 
       limitViolations, 
       iccMagnitude, 
       FaultResult.Status.SUCCESS
   );
   ```

### Data Model Gaps

| Internal Model | API Model | Status |
|----------------|-----------|--------|
| `ShortCircuitFault.ShortCircuitType` | `Fault.FaultType` | ⚠️ Misalignment |
| Internal enum has: TRIPHASED_GROUND, BIPHASED, BIPHASED_GROUND, MONOPHASED | API has: THREE_PHASE, SINGLE_PHASE | ⚠️ Extra types not in API |
| `ShortCircuitFault` | `BusFault` / `BranchFault` | ⚠️ Custom implementation |

---

## 📊 PRIORITY ROADMAP

### 🔴 **CRITICAL (Must Have)**

1. **Implement ShortCircuitParameters API Mapping**
   - Create proper parameter adapter from API to internal parameters
   - Support all mandatory parameters
   - File: New `ShortCircuitParametersAdapter.java`

2. **Complete FaultResult Output**
   - Calculate `shortCircuitPower` correctly (not 0)
   - Add voltage results
   - Implement proper status handling
   - File: [OpenShortCircuitProvider.java:128](sc-implementation/src/main/java/com/powsybl/sc/implementation/OpenShortCircuitProvider.java#L128)

3. **Support BranchFault**
   - Implement fault on lines/branches
   - Add proportional location support
   - File: [OpenShortCircuitProvider.java:186-195](sc-implementation/src/main/java/com/powsybl/sc/implementation/OpenShortCircuitProvider.java#L186-L195)

### 🟡 **HIGH (Should Have)**

4. **Voltage Profile Results (ShortCircuitBusResults)**
   - Implement `with-voltage-result` parameter
   - Return voltage at all buses
   - Implement voltage drop threshold filtering

5. **Fortescue Results**
   - Implement `with-fortescue-result` parameter
   - Create FortescueFaultResult class
   - Return magnitude and angle per phase

6. **Limit Violations**
   - Implement `with-limit-violations` parameter
   - Check against LOW/HIGH_SHORT_CIRCUIT_CURRENT limits
   - Return violations list

7. **Voltage Ranges Configuration**
   - Implement `initial-voltage-profile-mode` = CONFIGURED
   - Support voltage-ranges JSON file
   - Apply coefficients per voltage level

### 🟢 **MEDIUM (Nice to Have)**

8. **Additional Parameters**
   - `with-loads`, `with-shunt-compensators`, `with-vsc-converter-stations`
   - `with-neutral-position`
   - `sub-transient-coefficient`
   - `min-voltage-drop-proportional-threshold`

9. **STEADY_STATE Period**
   - Add STEADY_STATE to PeriodType
   - Implement steady-state reactance logic

10. **ConnectionType.PARALLEL**
    - Support parallel fault connection
    - Convert to equivalent series

11. **FaultParameters per-fault override**
    - Implement per-fault parameter override
    - Map to internal parameters

### 🔵 **LOW (Could Have)**

12. **Battery Short-Circuit Extension**
    - Create BatteryShortCircuit extension
    - Similar to GeneratorShortCircuit2

13. **Debug and Reporting**
    - `detailedReport` parameter
    - `debugDir` parameter
    - Enhanced logging

---

## 📝 TECHNICAL DEBT

### Code Quality Issues

1. **TODOs in Code**
   - Line 82: "TODO: make distinction with 10% tolerance parameter"
   - Line 119: "TODO: check that assumption to use ratedU2 is always correct"
   - Line 122: "TODO: see how this could be improved"
   - Line 193: "TODO: transform parallel input into series"
   - Line 202: "TODO: see how to get lfBus from iidm Bus"
   - Line 215: "TODO improve"

2. **Warnings in Implementation**
   - Multiple "not yet supported" warnings that fail silently
   - Could lead to incorrect results if users aren't aware

3. **Norm Selection**
   - Line 93: Creates `ShortCircuitNormNone()` instead of `ShortCircuitNormIec()`
   - IEC 60909 support exists but isn't used by default!

---

## 🎯 RECOMMENDATION

**Immediate Actions:**
1. Fix the default norm to use IEC 60909
2. Implement ShortCircuitParameters adapter
3. Calculate short-circuit power correctly
4. Add BranchFault support

**Short-term (1-2 months):**
5. Complete voltage profile results
6. Implement Fortescue results
7. Add limit violations

**Medium-term (3-6 months):**
8. Remaining parameters
9. Battery extension
10. Complete documentation

---

## 📖 References

- [PowSyBl Core Documentation - Short-Circuit](https://powsybl.readthedocs.io/projects/powsybl-core/en/stable/simulation/shortcircuit/)
- [Parameters Specification](https://powsybl.readthedocs.io/projects/powsybl-core/en/stable/simulation/shortcircuit/parameters.html)
- [Inputs Specification](https://powsybl.readthedocs.io/projects/powsybl-core/en/stable/simulation/shortcircuit/inputs.html)
- [Outputs Specification](https://powsybl.readthedocs.io/projects/powsybl-core/en/stable/simulation/shortcircuit/outputs.html)
- [IEC 60909 Standard Implementation](sc-implementation/src/main/java/com/powsybl/sc/implementation/ShortCircuitNormIec.java)

---

*Document generated on 2025-12-16*
*Based on powsybl-core v7.1.0 API specification*
