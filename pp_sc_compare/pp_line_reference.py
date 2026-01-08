#!/usr/bin/env python3
import argparse
import csv

from pandapower.shortcircuit import calc_sc
from pandapower.create import (
    create_empty_network,
    create_bus,
    create_ext_grid,
    create_line,
)


def line_network(length_km, c0_nf_per_km):
    net = create_empty_network()
    b1 = create_bus(net, 110, name="B1")
    b2 = create_bus(net, 110, name="B2")
    create_ext_grid(net, b1, s_sc_max_mva=100.0, s_sc_min_mva=80.0, rx_min=0.20, rx_max=0.35)
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
    return net


def run(case, fault, r_fault, x_fault, length_km, c0_nf_per_km):
    net = line_network(length_km, c0_nf_per_km)
    if case == "min":
        net.line["endtemp_degree"] = 80
    if fault == "1ph":
        net.ext_grid["r0x0_max"] = 0.4
        net.ext_grid["x0x_max"] = 1.0
        net.ext_grid["r0x0_min"] = 0.4
        net.ext_grid["x0x_min"] = 1.0
        calc_sc(net, fault="1ph", case=case, r_fault_ohm=r_fault, x_fault_ohm=x_fault)
    else:
        calc_sc(net, case=case, r_fault_ohm=r_fault, x_fault_ohm=x_fault)
    return net


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--case", choices=("max", "min"), default="max")
    parser.add_argument("--fault", choices=("3ph", "1ph"), default="3ph")
    parser.add_argument("--r-fault", type=float, default=0.0)
    parser.add_argument("--x-fault", type=float, default=0.0)
    parser.add_argument("--length-km", type=float, default=15.0)
    parser.add_argument("--c0-nf-per-km", type=float, default=2000.0)
    parser.add_argument("--out", required=True)
    args = parser.parse_args()

    net = run(args.case, args.fault, args.r_fault, args.x_fault, args.length_km, args.c0_nf_per_km)
    with open(args.out, "w", newline="") as fh:
        writer = csv.writer(fh)
        writer.writerow(["busId", "ikss_ka"])
        for idx, row in net.res_bus_sc.iterrows():
            bus_id = net.bus.at[idx, "name"]
            writer.writerow([bus_id, row["ikss_ka"]])


if __name__ == "__main__":
    main()
