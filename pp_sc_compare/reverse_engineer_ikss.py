#!/usr/bin/env python3
"""Reverse-engineer the fault current calculation from pandapower results"""
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

# Calculate both faults
net_3ph = build_network()
calc_sc(net_3ph, case="max")

net_1ph = build_network()
calc_sc(net_1ph, fault="1ph", case="max")

bus_kv = 110.0
un_v = bus_kv * 1000.0
sqrt3 = np.sqrt(3.0)

print("=== THREE-PHASE FAULT (BALANCED) ===")
ik_3ph = net_3ph.res_bus_sc.at[0, 'ikss_ka']
rk = net_3ph.res_bus_sc.at[0, 'rk_ohm']
xk = net_3ph.res_bus_sc.at[0, 'xk_ohm']
zk = complex(rk, xk)

print(f"ikss_ka = {ik_3ph:.6f} kA")
print(f"rk_ohm = {rk:.6f} Ω")
print(f"xk_ohm = {xk:.6f} Ω")
print(f"|Zk| = {abs(zk):.6f} Ω")

# Verify: Ik_3ph = Un / (√3 * Z1)
ik_3ph_calc = (un_v / sqrt3) / abs(zk) / 1000.0
print(f"\nVerification: Un/(√3*|Zk|) = {ik_3ph_calc:.6f} kA")
print(f"Match: {np.isclose(ik_3ph, ik_3ph_calc)}")

print("\n=== SINGLE-PHASE FAULT (UNBALANCED) ===")
ik_1ph = net_1ph.res_bus_sc.at[0, 'ikss_ka']
rk0 = net_1ph.res_bus_sc.at[0, 'rk0_ohm']
xk0 = net_1ph.res_bus_sc.at[0, 'xk0_ohm']
zk0 = complex(rk0, xk0)

print(f"ikss_ka = {ik_1ph:.6f} kA")
print(f"rk0_ohm = {rk0:.6f} Ω")
print(f"xk0_ohm = {xk0:.6f} Ω")
print(f"|Zk0| = {abs(zk0):.6f} Ω")

# For single-phase fault: Ik = 3*Un / (√3 * (2*Z1 + Z0))
# Let's assume Z1 = Z2 = Zk from 3-phase fault
# Then: Ik_1ph = 3*Un / (√3 * (2*Zk + Z0))

# Can we reverse-engineer Z0 from this?
# Ik_1ph * √3 * (2*Zk + Z0) = 3*Un
# (2*Zk + Z0) = 3*Un / (√3 * Ik_1ph)
# Z0 = 3*Un / (√3 * Ik_1ph) - 2*Zk

z_equiv_from_ik = (3 * un_v) / (sqrt3 * ik_1ph * 1000.0)
z0_calc = z_equiv_from_ik - 2 * abs(zk)

print(f"\n=== REVERSE ENGINEERING ===")
print(f"(2*Z1 + Z0) should equal: {z_equiv_from_ik:.6f} Ω")
print(f"2*Z1 = 2 × {abs(zk):.6f} = {2*abs(zk):.6f} Ω")
print(f"Therefore Z0 = {z0_calc:.6f} Ω")

# But pandapower reports |Zk0| = 256.57 Ω, which is the magnitude of the COMPLEX number rk0+jxk0
print(f"\nPandapower reports |rk0+jxk0| = {abs(zk0):.6f} Ω")

# Maybe rk0/xk0 are not the actual Z0, but rather represent the denominator (2*Z1+Z0)?
# Let's check if |rk0+jxk0| = |2*Z1+Z0|

z_denom = 2 * zk + zk0  # If zk0 were the actual Z0
print(f"\nIf Zk0 were Z0: 2*Zk + Zk0 = {z_denom:.6f} Ω, |.| = {abs(z_denom):.6f} Ω")

# Or maybe pandapower stores (2*Z1+Z0) directly in rk0/xk0?
ik_1ph_test = (3 * un_v) / (sqrt3 * abs(zk0)) / 1000.0
print(f"\nIf rk0/xk0 = (2*Z1+Z0): Ik = 3*Un/(√3*|rk0+jxk0|) = {ik_1ph_test:.6f} kA")
print(f"Match with actual Ik: {np.isclose(ik_1ph, ik_1ph_test)}")

# That doesn't match either. Let's try the complex formula (not just magnitude)
# Ik_1ph = 3*Un / (√3 * (2*Z1 + Z0))
# where Z1 and Z0 are complex

z_total_complex = 2 * zk + zk0  # Assuming zk0 is actually Z0
ik_1ph_complex = (3 * un_v / sqrt3) / abs(z_total_complex) / 1000.0
print(f"\nUsing complex impedances: Ik = {ik_1ph_complex:.6f} kA")
print(f"Match: {np.isclose(ik_1ph, ik_1ph_complex)}")

print("\n=== CONCLUSION ===")
print(f"The value rk0={rk0:.6f}, xk0={xk0:.6f} reported by pandapower")
print(f"represents the ZERO-SEQUENCE Thevenin impedance Z0,")
print(f"NOT the total denominator (2*Z1+Z0).")
print(f"\nThe discrepancy suggests our calculation of Z0 is still incorrect.")
