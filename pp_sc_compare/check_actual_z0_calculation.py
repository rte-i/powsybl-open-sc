#!/usr/bin/env python3
"""Check if pandapower's rk0/xk0 might be something other than just Z0"""
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

# Test with 3-phase and 1-phase
net_3ph = build_network()
calc_sc(net_3ph, case="max")

net_1ph = build_network()
calc_sc(net_1ph, fault="1ph", case="max")

bus_kv = 110.0
un_v = bus_kv * 1000.0
sqrt3 = np.sqrt(3.0)

print("=== 3-PHASE FAULT ===")
ik_3ph = net_3ph.res_bus_sc.at[0, 'ikss_ka']
rk = net_3ph.res_bus_sc.at[0, 'rk_ohm']
xk = net_3ph.res_bus_sc.at[0, 'xk_ohm']
zk = complex(rk, xk)

print(f"Ik'' = {ik_3ph:.6f} kA")
print(f"Zk = {rk:.6f} + j{xk:.6f} Ω")
print(f"|Zk| = {abs(zk):.6f} Ω")

# Try different formulas
# Formula 1: Ik = c*Un/(√3*|Z1|) where c is voltage factor
c_max = 1.1  # IEC 60909 voltage factor for max current
ik_theory1 = c_max * (un_v / sqrt3) / abs(zk) / 1000.0
print(f"\nTheory with c=1.1: {ik_theory1:.6f} kA")
print(f"Match: {np.isclose(ik_3ph, ik_theory1, rtol=0.01)}")

print("\n=== 1-PHASE FAULT ===")
ik_1ph = net_1ph.res_bus_sc.at[0, 'ikss_ka']
rk0 = net_1ph.res_bus_sc.at[0, 'rk0_ohm']
xk0 = net_1ph.res_bus_sc.at[0, 'xk0_ohm']
zk0 = complex(rk0, xk0)

print(f"Ik'' = {ik_1ph:.6f} kA")
print(f"Zk0 = {rk0:.6f} + j{xk0:.6f} Ω")
print(f"|Zk0| = {abs(zk0):.6f} Ω")

# Standard single-phase fault formula: Ik = 3*Un / (√3 * (2*Z1 + Z0))
# But pandapower might use: Ik = c*3*Un / (√3 * (2*Z1 + Z0))

# Let's reverse-engineer what the denominator should be
# From Ik = c*3*Un / (√3 * Z_denom)
# Z_denom = c*3*Un / (√3 * Ik)

z_denom_calc = c_max * 3 * un_v / (sqrt3 * ik_1ph * 1000.0)
print(f"\nReverse-engineered denominator:")
print(f"c*3*Un/(√3*Ik) = {z_denom_calc:.6f} Ω")

# If Z_denom = 2*Z1 + Z0
# Then Z0 = Z_denom - 2*Z1
z0_calc = z_denom_calc - 2*abs(zk)
print(f"Z0 = Z_denom - 2*|Z1| = {z0_calc:.6f} Ω")

# But pandapower reports |Z0| = 256.75 Ω
print(f"\nPandapower |Zk0| = {abs(zk0):.6f} Ω")

# Maybe rk0/xk0 represent something else?
# Let's check if they could be 2*Z1 + Z0
z_sum = 2*zk + zk0
ik_test1 = c_max * 3 * un_v / (sqrt3 * abs(z_sum)) / 1000.0
print(f"\nIf Zk0 is Z0, then Ik = c*3*Un/(√3*|2*Z1+Z0|) = {ik_test1:.6f} kA")
print(f"Match: {np.isclose(ik_1ph, ik_test1, rtol=0.01)}")

# What if we just use complex arithmetic properly?
# The formula is vectorial: Ik = c*3*Un / (√3 * (2*Z1 + Z0))
# where Z1 and Z0 are complex impedances
z_total_complex = 2*zk + zk0
ik_test2 = c_max * 3 * un_v / (sqrt3 * abs(z_total_complex)) / 1000.0
print(f"\nUsing complex: Ik = c*3*Un/(√3*|2*Z1+Z0|) = {ik_test2:.6f} kA")
print(f"Match: {np.isclose(ik_1ph, ik_test2, rtol=0.01)}")

# Maybe pandapower stores Z0 but from bus B2 perspective?
# Or includes some transformation?

print("\n=== DETAILED ANALYSIS ===")
print(f"If formula is Ik = c*3*Un/(√3*Z_eq):")
print(f"  Z_eq should be: {z_denom_calc:.6f} Ω")
print(f"  2*|Z1| = {2*abs(zk):.6f} Ω")
print(f"  Therefore |Z0| should be: {z0_calc:.6f} Ω")
print(f"  But pandapower reports |Zk0| = {abs(zk0):.6f} Ω")
print(f"  Ratio: |Zk0|/|Z0_calc| = {abs(zk0)/abs(z0_calc) if z0_calc != 0 else 'inf':.6f}")

# Let's also check B2
print("\n=== BUS B2 ===")
ik_1ph_b2 = net_1ph.res_bus_sc.at[1, 'ikss_ka']
rk0_b2 = net_1ph.res_bus_sc.at[1, 'rk0_ohm']
xk0_b2 = net_1ph.res_bus_sc.at[1, 'xk0_ohm']
print(f"B2 Ik'' = {ik_1ph_b2:.6f} kA")
print(f"B2 Zk0 = {rk0_b2:.6f} + j{xk0_b2:.6f} Ω")

print(f"\nDifference B1-B2:")
print(f"ΔR = {rk0 - rk0_b2:.6f} Ω")
print(f"ΔX = {xk0 - xk0_b2:.6f} Ω")

# This should be related to the line impedance
print(f"\nLine R0 = {0.244 * 15:.6f} Ω")
print(f"Line X0 (inductive) = {0.336 * 15:.6f} Ω")
