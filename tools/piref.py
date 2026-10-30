#!/usr/bin/env python3
"""Reference digits of pi (Machin's formula) for tools/pi.m verification.

Usage: python3 piref.py N   -> prints floor(pi * 10^N).
Machin: pi = 16*atan(1/5) - 4*atan(1/239), computed in scaled integers
(same method and guard-digit convention as tools/pi.m: pass N including
the guard digits, e.g. 100025 to verify a pi.m run at N=100000).
"""
import sys


def atan_inv(x, n):
    # floor(10^n * atan(1/x)) via the alternating series, all integer
    p = 10 ** n // x
    s = p
    xx = x * x
    k = 1
    while p != 0:
        p //= xx
        if k % 2 == 1:
            s -= p // (2 * k + 1)
        else:
            s += p // (2 * k + 1)
        k += 1
    return s


def main():
    n = int(sys.argv[1])
    sys.set_int_max_str_digits(max(n + 100, 660))
    print(16 * atan_inv(5, n) - 4 * atan_inv(239, n))


if __name__ == "__main__":
    main()
