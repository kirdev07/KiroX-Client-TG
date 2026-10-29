#!/usr/bin/env python3
"""Verify the exact user-approved Fusion six-screen reference.

Run: python3 design-reference/fusion-v2/check_approved_reference.py
Only validates a reference image. Does not touch TDLib or Android code.
"""
from pathlib import Path
from hashlib import sha256
import struct
import sys

HERE = Path(__file__).resolve().parent
NAME = "assets/KiroX_Fusion_Expressive_Glass_APPROVED_ORIGINAL.png"
EXPECTED_SHA = "009265773e852eac2d944835d0fcbde1818da2a7296f751297065bfb23d2d4f3"
EXPECTED_RES = (1536, 620)
p = HERE / NAME

if not p.is_file():
    print("MISSING_APPROVED_PNG: ", p)
    print("Use the original user-approved PNG from 29 October 2026. Do not substitute another AI mockup.")
    sys.exit(2)
data = p.read_bytes()
if not data.startswith(b"\x89PNG\r\n\x1a\n") or len(data) < 24:
    print("INVALID_PNG: input is not an ordinary PNG")
    sys.exit(3)
size = struct.unpack(">II", data[16:24])
digest = sha256(data).hexdigest()
if size != EXPECTED_RES or digest != EXPECTED_SHA:
    print("WRONG_REFERENCE: expected exactly 1536x620 PNG and owner-verified SHA256")
    print("Actual dimensions:", size, "Actual SHA256:", digest)
    sys.exit(4)
print("APPROVED_REFERENCE_OK:", p)
print("Verified dimensions:", size, "SHA256:", digest)
