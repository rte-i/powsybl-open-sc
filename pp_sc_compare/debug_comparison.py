#!/usr/bin/env python3
"""Debug script to understand the difference between OpenSC and pandapower"""
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

# Calculate 3-phase fault
net_3ph = build_network()
calc_sc(net_3ph, case="max")
print("=== THREE-PHASE FAULT ===")
print(net_3ph.res_bus_sc[["ikss_ka"]])
print(f"\nB1: {net_3ph.res_bus_sc.at[0, 'ikss_ka']:.6f} kA")
print(f"B2: {net_3ph.res_bus_sc.at[1, 'ikss_ka']:.6f} kA")

# Calculate 1-phase fault
net_1ph = build_network()
calc_sc(net_1ph, fault="1ph", case="max")
print("\n=== SINGLE-PHASE FAULT ===")
print(net_1ph.res_bus_sc[["ikss_ka"]])
print(f"\nB1: {net_1ph.res_bus_sc.at[0, 'ikss_ka']:.6f} kA")
print(f"B2: {net_1ph.res_bus_sc.at[1, 'ikss_ka']:.6f} kA")

# Calculate ratio
ratio = net_1ph.res_bus_sc.at[0, 'ikss_ka'] / net_3ph.res_bus_sc.at[0, 'ikss_ka']
print(f"\nRatio 1ph/3ph at B1: {ratio:.6f}")
print(f"Expected if Z1=Z2 and Z0>>Z1: ~0.75 (= 3/(2+1))")

# Show impedance details
print("\n=== IMPEDANCE DETAILS ===")
print(f"Z1 (direct): rk={net_3ph.res_bus_sc.at[0, 'rk_ohm']:.6f}, xk={net_3ph.res_bus_sc.at[0, 'xk_ohm']:.6f}")
print(f"Z0 (zero): rk0={net_1ph.res_bus_sc.at[0, 'rk0_ohm']:.6f}, xk0={net_1ph.res_bus_sc.at[0, 'xk0_ohm']:.6f}")

z1 = complex(net_3ph.res_bus_sc.at[0, 'rk_ohm'], net_3ph.res_bus_sc.at[0, 'xk_ohm'])
z0 = complex(net_1ph.res_bus_sc.at[0, 'rk0_ohm'], net_1ph.res_bus_sc.at[0, 'xk0_ohm'])
print(f"\nZ1 magnitude: {abs(z1):.6f} Ω")
print(f"Z0 magnitude: {abs(z0):.6f} Ω")
print(f"Z0/Z1 ratio: {abs(z0)/abs(z1):.6f}")

# Theoretical calculation
un_kv = 110.0
ik_3ph_theory = (un_kv / 1.732) / abs(z1)  # Ik'' = Un/(√3 * Z1)
ik_1ph_theory = (3 * un_kv / 1.732) / (2 * abs(z1) + abs(z0))  # Ik'' = 3*Un/(√3*(2*Z1+Z0))

print(f"\n=== THEORETICAL VERIFICATION ===")
print(f"3-phase Ik'' theory: {ik_3ph_theory:.6f} kA vs pandapower: {net_3ph.res_bus_sc.at[0, 'ikss_ka']:.6f} kA")
print(f"1-phase Ik'' theory: {ik_1ph_theory:.6f} kA vs pandapower: {net_1ph.res_bus_sc.at[0, 'ikss_ka']:.6f} kA")
