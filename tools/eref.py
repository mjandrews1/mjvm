#!/usr/bin/env python3
"""Reference digits of e for tools/e.m verification.

Usage: python3 eref.py N   -> prints floor(e*10^N) (N+1 digits, leading '2').
Uses the decimal module (correct rounding), independent of e.m's
truncating fixed-point method; guard digits in e.m absorb the difference.
"""
import sys
from decimal import Decimal, getcontext

def main():
    n = int(sys.argv[1])
    getcontext().prec = n + 40
    e = Decimal(1)
    term = Decimal(1)
    k = 1
    while term != 0:
        term = term / k
        e += term
        k += 1
    scaled = int(e.scaleb(n))
    print(scaled)

if __name__ == "__main__":
    sys.set_int_max_str_digits(2000000)
    main()
