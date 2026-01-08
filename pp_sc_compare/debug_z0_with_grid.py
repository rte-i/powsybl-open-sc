#!/usr/bin/env python3
"""Debug Z0 including ext_grid contribution"""
import pandas as pd
import pandapower as pp
from pandapower.shortcircuit import calc_sc
from pandapower.create import create_empty_network, create_bus, create_ext_grid, create_line
import numpy as np

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
    net.ext_grid["r0x0_max"] = 0.4  # R0/X0 ratio for ext_grid
    net.ext_grid["x0x_max"] = 1.0   # X0/X1 ratio for ext_grid
    return net

net = build_network()
calc_sc(net, fault="1ph", case="max")

print("=== EXT_GRID ZERO-SEQUENCE IMPEDANCE ===")
# ext_grid parameters
s_sc_mva = net.ext_grid.at[0, 's_sc_max_mva']
rx_max = net.ext_grid.at[0, 'rx_max']
r0x0_max = net.ext_grid.at[0, 'r0x0_max']
x0x_max = net.ext_grid.at[0, 'x0x_max']
bus_kv = 110.0

# Calculate ext_grid positive sequence impedance
z1_grid = (bus_kv ** 2) / s_sc_mva
x1_grid = z1_grid / np.sqrt(1 + rx_max ** 2)
r1_grid = rx_max * x1_grid

print(f"Ext_grid positive sequence:")
print(f"  Z1 = {z1_grid:.6f} Ω")
print(f"  R1 = {r1_grid:.6f} Ω")
print(f"  X1 = {x1_grid:.6f} Ω")

# Calculate ext_grid zero sequence impedance
x0_grid = x0x_max * x1_grid
r0_grid = r0x0_max * x0_grid

print(f"\nExt_grid zero sequence:")
print(f"  X0 = {x0_grid:.6f} Ω (= {x0x_max} × X1)")
print(f"  R0 = {r0_grid:.6f} Ω (= {r0x0_max} × X0)")
print(f"  Z0_grid = {complex(r0_grid, x0_grid):.6f} Ω")

print("\n=== LINE ZERO-SEQUENCE IMPEDANCE ===")
r0_line = 0.244 * 15.0
x0_line_inductive = 0.336 * 15.0
c0_nf = 2000.0 * 15.0
c0_f = c0_nf * 1e-9
b0_s = 2 * np.pi * 50.0 * c0_f

print(f"Line zero sequence (series):")
print(f"  R0 = {r0_line:.6f} Ω")
print(f"  X0 = {x0_line_inductive:.6f} Ω (inductive)")
print(f"  B0 = {b0_s:.9f} S (capacitive shunt)")

# The line with shunt admittance in parallel with series impedance
z0_line_series = complex(r0_line, x0_line_inductive)
y0_line_shunt = complex(0, b0_s)

# For a line connected at one end (as seen from B1 during a fault):
# The shunt admittance is in parallel with the series impedance
# Z_line_eq = Z_series || (1/Y_shunt)
# Y_total = 1/Z_series + Y_shunt
# Z_line_eq = 1 / Y_total

y0_line_total = 1.0/z0_line_series + y0_line_shunt
z0_line_eq = 1.0 / y0_line_total

print(f"\nLine equivalent impedance (series || shunt):")
print(f"  Z0_line = {z0_line_eq:.6f} Ω")
print(f"  R0_line = {z0_line_eq.real:.6f} Ω")
print(f"  X0_line = {z0_line_eq.imag:.6f} Ω")

print("\n=== TOTAL ZERO-SEQUENCE IMPEDANCE AT B1 ===")
# At bus B1, we have ext_grid in series with line
# (line is connected to B1, fault is at B1, so we see ext_grid + line in series)
z0_total = complex(r0_grid, x0_grid) + z0_line_eq

print(f"Z0_total = Z0_grid + Z0_line:")
print(f"  Z0_total = {z0_total:.6f} Ω")
print(f"  R0_total = {z0_total.real:.6f} Ω")
print(f"  X0_total = {z0_total.imag:.6f} Ω")

print(f"\n=== COMPARISON WITH PANDAPOWER ===")
print(f"Pandapower Z0 at B1:")
print(f"  rk0_ohm = {net.res_bus_sc.at[0, 'rk0_ohm']:.6f} Ω")
print(f"  xk0_ohm = {net.res_bus_sc.at[0, 'xk0_ohm']:.6f} Ω")

print(f"\nDifference:")
print(f"  ΔR0 = {abs(net.res_bus_sc.at[0, 'rk0_ohm'] - z0_total.real):.6f} Ω")
print(f"  ΔX0 = {abs(net.res_bus_sc.at[0, 'xk0_ohm'] - z0_total.imag):.6f} Ω")
