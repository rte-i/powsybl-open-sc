#!/usr/bin/env python3
"""Debug how pandapower calculates Z0 with capacitance"""
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
    net.ext_grid["r0x0_max"] = 0.4
    net.ext_grid["x0x_max"] = 1.0
    return net

# Calculate 1-phase fault
net = build_network()
calc_sc(net, fault="1ph", case="max")

print("=== PANDAPOWER INTERNAL CALCULATIONS ===")
print(f"\nLine parameters:")
print(f"r0_ohm_per_km: {net.line.at[0, 'r0_ohm_per_km']}")
print(f"x0_ohm_per_km: {net.line.at[0, 'x0_ohm_per_km']}")
print(f"c0_nf_per_km: {net.line.at[0, 'c0_nf_per_km']}")
print(f"length_km: {net.line.at[0, 'length_km']}")

print(f"\nResults at B1:")
print(f"rk0_ohm: {net.res_bus_sc.at[0, 'rk0_ohm']:.6f}")
print(f"xk0_ohm: {net.res_bus_sc.at[0, 'xk0_ohm']:.6f}")
print(f"ikss_ka: {net.res_bus_sc.at[0, 'ikss_ka']:.6f}")

# Try to understand the Z0 calculation
# Looking at the results, xk0 is NEGATIVE, which means capacitive effect
# Let's reverse-engineer the calculation

r0_total = 0.244 * 15.0
x0_total_inductive = 0.336 * 15.0
c0_total_nf = 2000.0 * 15.0
c0_total_f = c0_total_nf * 1e-9

# Capacitive susceptance
freq = 50.0
b0_s = 2 * np.pi * freq * c0_total_f
x0_capacitive = -1.0 / b0_s  # Negative because capacitive

print(f"\n=== MANUAL CALCULATION ===")
print(f"R0_series = {r0_total:.6f} Ω")
print(f"X0_series (inductive) = {x0_total_inductive:.6f} Ω")
print(f"B0 (capacitive) = {b0_s:.9f} S")
print(f"X0_capacitive = {x0_capacitive:.6f} Ω")

# For a line with shunt admittance, the equivalent impedance seen from one end
# is NOT just Z_series - j/B_shunt. We need to use the ABCD parameters or
# consider that pandapower might be using a different model.

# Let's check if pandapower uses a pi-model or equivalent-pi for Z0
# In a pi-model with shunt B/2 at each end:
# Z_eq ≈ Z_series / (1 + Z_series * Y_shunt / 2)
# For large Y_shunt, Z_eq ≈ Z_series / (Z_series * Y_shunt / 2) = 2 / Y_shunt

y0_shunt = complex(0, b0_s)
z0_series = complex(r0_total, x0_total_inductive)

# Try different models
print(f"\n=== TRYING DIFFERENT MODELS ===")

# Model 1: Series Z + parallel Y
z0_model1 = z0_series + 1 / y0_shunt
print(f"Model 1 (Z_series + Z_shunt_parallel): {z0_model1:.6f} Ω")
print(f"  → R={z0_model1.real:.6f}, X={z0_model1.imag:.6f}")

# Model 2: Pi-model (exact formula for symmetric pi)
# Z_eq = Z_series / (1 + Z_series * Y_shunt / 2)
z0_model2 = z0_series / (1 + z0_series * y0_shunt / 2.0)
print(f"Model 2 (Pi-model exact): {z0_model2:.6f} Ω")
print(f"  → R={z0_model2.real:.6f}, X={z0_model2.imag:.6f}")

# Model 3: Just shunt admittance (for very high capacitance)
z0_model3 = 1.0 / y0_shunt
print(f"Model 3 (Just shunt): {z0_model3:.6f} Ω")
print(f"  → R={z0_model3.real:.6f}, X={z0_model3.imag:.6f}")

# Model 4: Series + shunt in parallel (total admittance)
y0_total = 1.0/z0_series + y0_shunt
z0_model4 = 1.0 / y0_total
print(f"Model 4 (Series || Shunt): {z0_model4:.6f} Ω")
print(f"  → R={z0_model4.real:.6f}, X={z0_model4.imag:.6f}")

print(f"\n=== COMPARISON WITH PANDAPOWER ===")
print(f"Pandapower Z0 = {complex(net.res_bus_sc.at[0, 'rk0_ohm'], net.res_bus_sc.at[0, 'xk0_ohm']):.6f} Ω")
print(f"  → R={net.res_bus_sc.at[0, 'rk0_ohm']:.6f}, X={net.res_bus_sc.at[0, 'xk0_ohm']:.6f}")
