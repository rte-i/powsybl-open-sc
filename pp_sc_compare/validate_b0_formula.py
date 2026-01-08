#!/usr/bin/env python3
"""Validate that adding B0 will fix the discrepancy"""
import math

# Network parameters
freq_hz = 50.0
bus_kv = 110.0
length_km = 15.0

# Line parameters
r0_ohm_per_km = 0.244
x0_ohm_per_km = 0.336
c0_nf_per_km = 2000.0

# Calculate homopolar impedance
r0 = r0_ohm_per_km * length_km
x0 = x0_ohm_per_km * length_km

# Calculate B0 from capacitance (this is what we added)
c0_total_f = c0_nf_per_km * 1e-9 * length_km
b0_siemens = 2.0 * math.pi * freq_hz * c0_total_f

print("=== HOMOPOLAR PARAMETERS ===")
print(f"R0 = {r0:.6f} Ω")
print(f"X0 = {x0:.6f} Ω (inductive)")
print(f"C0 total = {c0_total_f*1e6:.6f} µF")
print(f"B0 = {b0_siemens:.9f} S = {b0_siemens*1e6:.6f} µS")

# Calculate effective homopolar reactance including capacitance
# Z0 = R0 + jX0 in series, with shunt susceptance jB0
# For shunt admittance Y0 = jB0, impedance is Z0_shunt = -j/B0
x0_capacitive = -1.0 / b0_siemens

print(f"\nCapacitive reactance: X0_cap = {x0_capacitive:.6f} Ω")

# Total homopolar impedance (approximate for small shunt admittance)
# For a pi-model, effective Z0 ≈ Z0_series - (Z0_series * Y0_shunt * Z0_series)/2
# But for high capacitance, the shunt effect dominates
# Simplified: X0_eff ≈ X0_inductive + X0_capacitive
x0_eff = x0 + x0_capacitive

print(f"\nEffective homopolar impedance:")
print(f"R0_eff = {r0:.6f} Ω")
print(f"X0_eff = {x0_eff:.6f} Ω (= {x0:.6f} + {x0_capacitive:.6f})")
print(f"Z0_eff magnitude = {math.sqrt(r0**2 + x0_eff**2):.6f} Ω")

# For comparison, direct sequence impedance (from previous tests)
r1_ohm_per_km = 0.153
x1_ohm_per_km = 0.166
r1 = r1_ohm_per_km * length_km
x1 = x1_ohm_per_km * length_km
z1_mag = math.sqrt(r1**2 + x1**2)

print(f"\n=== SEQUENCE IMPEDANCE COMPARISON ===")
print(f"Z1 magnitude = {z1_mag:.6f} Ω")
print(f"Z0 magnitude (without B0) = {math.sqrt(r0**2 + x0**2):.6f} Ω")
print(f"Z0 magnitude (with B0) = {math.sqrt(r0**2 + x0_eff**2):.6f} Ω")

# Theoretical single-phase fault current calculation
# Ik_1ph = 3*Un / (√3 * (2*Z1 + Z0))
un_v = bus_kv * 1000.0
sqrt3 = math.sqrt(3.0)

# Without B0
z0_without_b0 = math.sqrt(r0**2 + x0**2)
ik_1ph_without_b0 = (3.0 * un_v / sqrt3) / (2.0 * z1_mag + z0_without_b0) / 1000.0

# With B0
z0_with_b0 = math.sqrt(r0**2 + x0_eff**2)
ik_1ph_with_b0 = (3.0 * un_v / sqrt3) / (2.0 * z1_mag + z0_with_b0) / 1000.0

print(f"\n=== THEORETICAL SINGLE-PHASE FAULT CURRENT ===")
print(f"Without B0: Ik'' = {ik_1ph_without_b0:.6f} kA")
print(f"With B0:    Ik'' = {ik_1ph_with_b0:.6f} kA")
print(f"Difference: {abs(ik_1ph_with_b0 - ik_1ph_without_b0):.6f} kA ({abs(ik_1ph_with_b0 - ik_1ph_without_b0)/ik_1ph_without_b0*100:.2f}%)")

print(f"\n=== COMPARISON WITH PANDAPOWER ===")
print(f"Pandapower result: 0.736578 kA")
print(f"OpenSC without B0: {ik_1ph_without_b0:.6f} kA (error: {abs(0.736578 - ik_1ph_without_b0)/0.736578*100:.2f}%)")
print(f"OpenSC with B0:    {ik_1ph_with_b0:.6f} kA (error: {abs(0.736578 - ik_1ph_with_b0)/0.736578*100:.2f}%)")
