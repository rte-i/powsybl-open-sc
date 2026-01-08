#!/usr/bin/env python3
import argparse

import pandapower as pp
from pandapower.shortcircuit import calc_sc
from pandapower.create import create_empty_network, create_bus, create_ext_grid, create_line


def build_network(length_km: float, c0_nf_per_km: float):
    net = create_empty_network()
    b1 = create_bus(net, 110, name="B1")
    b2 = create_bus(net, 110, name="B2")
    create_ext_grid(net, b1, s_sc_max_mva=100.0, s_sc_min_mva=80.0, rx_min=0.2, rx_max=0.35)
    create_line(
        net,
        b1,
        b2,
        length_km=length_km,
        std_type="N2XS(FL)2Y 1x120 RM/35 64/110 kV",
    )
    net.line["r0_ohm_per_km"] = 0.244
    net.line["x0_ohm_per_km"] = 0.336
    net.line["c0_nf_per_km"] = c0_nf_per_km
    net.ext_grid["r0x0_max"] = 0.4
    net.ext_grid["x0x_max"] = 1.0
    net.ext_grid["r0x0_min"] = 0.4
    net.ext_grid["x0x_min"] = 1.0
    return net


def compute_homopolar(length_km: float, c0_nf_per_km: float):
    v_nom_kv = 110.0
    sb_va = 100e6
    z_base = (v_nom_kv * 1000.0) ** 2 / sb_va
    r0_total = 0.244 * length_km
    x0_total = 0.336 * length_km
    ro = r0_total / z_base
    xo = x0_total / z_base
    freq = 50.0
    b0_total = 2.0 * 3.141592653589793 * freq * c0_nf_per_km * 1e-9 * length_km
    y_base = sb_va / (v_nom_kv * 1000.0) ** 2
    b0_pu = b0_total / y_base
    bom = b0_pu / 2.0
    return ro, xo, bom, b0_pu


def main():
    parser = argparse.ArgumentParser(description="Compare zero seq data pandas/OpenSC")
    parser.add_argument("--length-km", type=float, default=15.0)
    parser.add_argument("--c0-nf-per-km", type=float, default=2000.0)
    parser.add_argument("--out", help="csv filename for pandapower res", default=None)
    args = parser.parse_args()

    net = build_network(args.length_km, args.c0_nf_per_km)
    calc_sc(net, fault="1ph", case="max")
    res = net.res_bus_sc.set_index(net.bus["name"])

    ro, xo, bom, b0_pu = compute_homopolar(args.length_km, args.c0_nf_per_km)

    print(f"Zero-sequence comparison for length={args.length_km} km, C0={args.c0_nf_per_km} nF/km")
    print(f"  Derived homopolar (pu): ro={ro:.6f}, xo={xo:.6f}, bom(⊥)={bom:.6f}, b0_total_pu={b0_pu:.6f}")
    print("  pandapower res_bus_sc:")
    print(res[["ikss_ka", "rk0_ohm", "xk0_ohm"]])
    if args.out:
        with open(args.out, "w", newline="") as fh:
            res[["ikss_ka", "rk0_ohm", "xk0_ohm"]].to_csv(fh)


if __name__ == "__main__":
    main()
