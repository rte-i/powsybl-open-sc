#!/usr/bin/env python3
"""Test avec des valeurs réalistes de c0_nf_per_km"""
import pandapower as pp
from pandapower.shortcircuit import calc_sc
from pandapower.create import create_empty_network, create_bus, create_ext_grid, create_line
import numpy as np

def test_with_c0(c0_value, description):
    """Test with specific c0 value"""
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
    net.line["c0_nf_per_km"] = c0_value
    net.ext_grid["r0x0_max"] = 0.4
    net.ext_grid["x0x_max"] = 1.0

    # 3-phase fault
    calc_sc(net, case="max")
    ik_3ph = net.res_bus_sc.at[0, 'ikss_ka']

    # 1-phase fault
    net_1ph = create_empty_network()
    b1 = create_bus(net_1ph, 110, name="B1")
    b2 = create_bus(net_1ph, 110, name="B2")
    create_ext_grid(net_1ph, b1, s_sc_max_mva=100.0, rx_max=0.35)
    create_line(
        net_1ph, b1, b2,
        length_km=15.0,
        std_type="N2XS(FL)2Y 1x120 RM/35 64/110 kV",
    )
    net_1ph.line["r0_ohm_per_km"] = 0.244
    net_1ph.line["x0_ohm_per_km"] = 0.336
    net_1ph.line["c0_nf_per_km"] = c0_value
    net_1ph.ext_grid["r0x0_max"] = 0.4
    net_1ph.ext_grid["x0x_max"] = 1.0

    calc_sc(net_1ph, fault="1ph", case="max")
    ik_1ph = net_1ph.res_bus_sc.at[0, 'ikss_ka']

    ratio = ik_1ph / ik_3ph

    # Calculate B0 effect
    c0_total_f = c0_value * 1e-9 * 15.0
    b0_siemens = 2 * np.pi * 50.0 * c0_total_f
    x0_capacitive = -1.0 / b0_siemens if b0_siemens > 0 else float('inf')

    print(f"\n{description}")
    print(f"  c0 = {c0_value} nF/km")
    print(f"  B0 = {b0_siemens:.6f} S")
    print(f"  X0_capacitive = {x0_capacitive:.3f} Ω")
    print(f"  Ik_3ph = {ik_3ph:.6f} kA")
    print(f"  Ik_1ph = {ik_1ph:.6f} kA")
    print(f"  Ratio 1ph/3ph = {ratio:.6f}")
    if ratio > 1.0:
        print(f"  ⚠ 1-phase fault is STRONGER than 3-phase!")

# Test with various c0 values
print("=== IMPACT DE C0 SUR LES DÉFAUTS MONOPHASÉS ===")

# Typical values for overhead lines
test_with_c0(5.0, "Ligne aérienne typique (overhead line)")
test_with_c0(10.0, "Ligne aérienne haute capacité")
test_with_c0(50.0, "Câble souterrain basse tension")

# Cable values
test_with_c0(100.0, "Câble souterrain MT typique")
test_with_c0(250.0, "Câble souterrain MT haute capacité")
test_with_c0(500.0, "Câble souterrain HT")

# Extreme values
test_with_c0(1000.0, "Câble souterrain très haute capacité")
test_with_c0(2000.0, "Valeur de test (ultra-capacitif)")

print("\n=== CONCLUSION ===")
print("Si c0 < 250 nF/km: impact de B0 probablement négligeable")
print("Si c0 > 500 nF/km: impact de B0 devient significatif")
print("Si c0 > 1000 nF/km: B0 domine, défaut 1ph peut dépasser 3ph!")
