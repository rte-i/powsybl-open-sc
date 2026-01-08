#!/usr/bin/env python3
"""Inspect internal ppc structure to understand Z0 calculation"""
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
calc_sc(net, fault="1ph", case="max")

print("=== INTERNAL PPC STRUCTURE ===")
if hasattr(net, '_ppc0'):
    ppc0 = net._ppc0
    print("\nZero-sequence PPC found!")
    print(f"Number of buses: {len(ppc0['bus'])}")
    print(f"Number of branches: {len(ppc0['branch'])}")

    print("\n=== BRANCH DATA (zero-sequence) ===")
    # Branch columns: F_BUS, T_BUS, BR_R, BR_X, BR_B, BR_G, ...
    for i, branch in enumerate(ppc0['branch']):
        print(f"\nBranch {i}:")
        print(f"  From bus {int(branch[0])} to bus {int(branch[1])}")
        print(f"  R (pu): {branch[2].real:.9f}")
        print(f"  X (pu): {branch[3].real:.9f}")
        print(f"  B (pu): {branch[4].real:.9f}")
        print(f"  G (pu): {branch[5].real:.9f}")

        # Convert to ohms
        bus_kv = 110.0
        sn_mva = net.sn_mva
        baseR = (bus_kv ** 2) / sn_mva
        print(f"  R (Ω): {branch[2].real * baseR:.6f}")
        print(f"  X (Ω): {branch[3].real * baseR:.6f}")
        if branch[4].real != 0:
            print(f"  B (S): {branch[4].real / baseR:.9f}")
            print(f"  X_capacitive (Ω): {-1/(branch[4].real / baseR):.6f}")
else:
    print("No _ppc0 found, trying _ppc1...")
    if hasattr(net, '_ppc1'):
        print("Found _ppc1 (positive sequence)")

# Try to access the impedance matrix directly
print("\n=== CHECKING FOR ZBUS ===")
if hasattr(net, '_is_elements'):
    print("Network has _is_elements")

# Look at results structure
print("\n=== RESULT STRUCTURE ===")
print("Columns in res_bus_sc:")
print(net.res_bus_sc.columns.tolist())
print("\nB1 results:")
print(net.res_bus_sc.loc[0])
