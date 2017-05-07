#!/bin/bash

# Helper script to install UnixBench on CentOS (for benchmarking a VPS before deciding to use this hoster)
# Start benchmark via ./Run from inside UnixBench subdirectory

yum install -y gcc gcc-c++ make libXext-devel && yum groupinstall -y "Development Tools" && yum install -y libX11-devel mesa-libGL-devel perl-Time-HiRes && cd /opt && git clone https://github.com/kdlucas/byte-unixbench.git && cd byte-unixbench/UnixBench && make