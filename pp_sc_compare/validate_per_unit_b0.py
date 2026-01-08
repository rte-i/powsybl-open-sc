#!/usr/bin/env python3
"""Validate per-unit calculation of B0 from pandapower source code"""
import numpy as np
import pandapower as pp
from pandapower.shortcircuit import calc_sc
from pandapower.create import create_empty_network, create_bus, create_ext_grid, create_line

def build_network():
    net = create_empty_network()
    b1 = create_bus(net, 110, name="B1")
    b2 = create_bus(net, 110, name="B2")
    create_ext_grid(net, b1, s_sc_max_mva=100.0, rx_max=0.35)
    create_line(
        net, b1, b2,
        length_km=15.0,
        std_type="N2XS(FL)2Y 1x120 RM/35 64/110 kV",
    )
    net.line["r0_ohm_per_km"] = 0.244
    net.line["x0_ohm_per_km"] = 0.336
    net.line["c0_nf_per_km"] = 2000.0
    net.ext_grid["r0x0_max"] = 0.4
    net.ext_grid["x0x_max"] = 1.0
    return net

net = build_network()

# Calculate based on pandapower source code (pd2ppc_zero.py lines 522-531)
bus_kv = 110.0
sn_mva = net.sn_mva  # Base power (default 1 MVA)
length_km = 15.0
c0_nf_per_km = 2000.0
parallel = 1
f_hz = 50.0

# Base impedance: Z_base = V^2 / S_base
baseR = (bus_kv ** 2) / sn_mva

print("=== PANDAPOWER PER-UNIT CALCULATION ===")
print(f"Bus voltage: {bus_kv} kV")
print(f"Base power (Sn): {sn_mva} MVA")
print(f"Base impedance (Zbase): {baseR:.6f} Ω")

# Pandapower formula (from source code):
# BR_B = 2 * f_hz * pi * c0_nf_per_km * 1e-9 * baseR * length * parallel
b0_pu = (
    2
    * f_hz
    * np.pi
    * c0_nf_per_km
    * 1e-9
    * baseR
    * length_km
    * parallel
)

print(f"\n=== ZERO-SEQUENCE CAPACITANCE ===")
print(f"c0_nf_per_km: {c0_nf_per_km} nF/km")
print(f"Length: {length_km} km")
print(f"C0_total: {c0_nf_per_km * length_km} nF = {c0_nf_per_km * length_km * 1e-9 * 1e6:.6f} µF")

# Susceptance in Siemens (absolute)
c0_total_f = c0_nf_per_km * 1e-9 * length_km
b0_siemens = 2 * np.pi * f_hz * c0_total_f
y_base = 1.0 / baseR

print(f"\nB0 (absolute): {b0_siemens:.9f} S")
print(f"Y_base: {y_base:.9f} S")
print(f"B0 (per-unit): {b0_pu:.9f} pu")
print(f"Verification: B0_siemens / Y_base = {b0_siemens / y_base:.9f} pu")
print(f"Match: {np.isclose(b0_pu, b0_siemens / y_base)}")

# Now let's see what this means for impedance
# In per-unit: Y_pu = G_pu + jB_pu
# For pure capacitance: Y_pu = jB_pu
# Impedance: Z_pu = 1 / Y_pu = 1 / (jB_pu) = -j / B_pu

z0_capacitive_pu = -1j / b0_pu
z0_capacitive_ohm = z0_capacitive_pu * baseR

print(f"\n=== CAPACITIVE IMPEDANCE ===")
print(f"Z0_capacitive (pu): {z0_capacitive_pu:.6f}")
print(f"Z0_capacitive (Ω): {z0_capacitive_ohm:.6f}")
print(f"  → R = {z0_capacitive_ohm.real:.6f} Ω")
print(f"  → X = {z0_capacitive_ohm.imag:.6f} Ω (capacitive, negative)")

# Line series impedance
r0_ohm_per_km = 0.244
x0_ohm_per_km = 0.336
r0_total = r0_ohm_per_km * length_km
x0_total = x0_ohm_per_km * length_km

# Convert to per-unit
r0_pu = r0_total / baseR
x0_pu = x0_total / baseR

print(f"\n=== LINE SERIES IMPEDANCE ===")
print(f"R0 (Ω): {r0_total:.6f}")
print(f"X0 (Ω): {x0_total:.6f}")
print(f"R0 (pu): {r0_pu:.9f}")
print(f"X0 (pu): {x0_pu:.9f}")

z0_series_pu = complex(r0_pu, x0_pu)
print(f"Z0_series (pu): {z0_series_pu:.9f}")

# In a pi-model, shunt admittance is split B/2 at each end
# The equivalent impedance for a line with shunt admittance:
# For very high shunt admittance, the line impedance is dominated by the shunt
#
# Using pi-model: the total admittance seen from one end with the other end open
# is Y_series in parallel with Y_shunt
# Y_total = 1/Z_series + jB_shunt
y0_series_pu = 1.0 / z0_series_pu
y0_shunt_pu = 1j * b0_pu
y0_total_pu = y0_series_pu + y0_shunt_pu
z0_line_pu = 1.0 / y0_total_pu

z0_line_ohm = z0_line_pu * baseR

print(f"\n=== LINE EQUIVALENT IMPEDANCE (series || shunt) ===")
print(f"Y0_series (pu): {y0_series_pu:.9f}")
print(f"Y0_shunt (pu): {y0_shunt_pu:.9f}")
print(f"Y0_total (pu): {y0_total_pu:.9f}")
print(f"Z0_line (pu): {z0_line_pu:.9f}")
print(f"Z0_line (Ω): {z0_line_ohm:.6f}")
print(f"  → R = {z0_line_ohm.real:.6f} Ω")
print(f"  → X = {z0_line_ohm.imag:.6f} Ω")

# Now add ext_grid impedance
s_sc_mva = 100.0
rx_max = 0.35
r0x0_max = 0.4
x0x_max = 1.0

z1_grid = (bus_kv ** 2) / s_sc_mva
x1_grid = z1_grid / np.sqrt(1 + rx_max ** 2)
r1_grid = rx_max * x1_grid
x0_grid = x0x_max * x1_grid
r0_grid = r0x0_max * x0_grid

z0_grid_ohm = complex(r0_grid, x0_grid)
z0_total_ohm = z0_grid_ohm + z0_line_ohm

print(f"\n=== EXT_GRID IMPEDANCE ===")
print(f"Z0_grid (Ω): {z0_grid_ohm:.6f}")

print(f"\n=== TOTAL ZERO-SEQUENCE IMPEDANCE AT B1 ===")
print(f"Z0_total = Z0_grid + Z0_line")
print(f"Z0_total (Ω): {z0_total_ohm:.6f}")
print(f"  → R = {z0_total_ohm.real:.6f} Ω")
print(f"  → X = {z0_total_ohm.imag:.6f} Ω")

# Compare with pandapower
calc_sc(net, fault="1ph", case="max")
rk0_pp = net.res_bus_sc.at[0, 'rk0_ohm']
xk0_pp = net.res_bus_sc.at[0, 'xk0_ohm']

print(f"\n=== COMPARISON WITH PANDAPOWER ===")
print(f"Pandapower rk0: {rk0_pp:.6f} Ω")
print(f"Pandapower xk0: {xk0_pp:.6f} Ω")
print(f"Calculated R0:  {z0_total_ohm.real:.6f} Ω")
print(f"Calculated X0:  {z0_total_ohm.imag:.6f} Ω")
print(f"ΔR: {abs(rk0_pp - z0_total_ohm.real):.6f} Ω")
print(f"ΔX: {abs(xk0_pp - z0_total_ohm.imag):.6f} Ω")
