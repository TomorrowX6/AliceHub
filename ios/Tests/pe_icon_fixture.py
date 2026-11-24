import struct


def pe_icon_fixture(pe_plus=False):
    side = 32
    pixels = bytes(
        channel
        for y in range(side)
        for x in range(side)
        for channel in ((30, 202, 253, 255) if abs(x - 15) + abs(y - 15) < 12 else (174, 51, 76, 255))
    )
    icon = struct.pack("<IiiHHIIiiII", 40, side, side * 2, 1, 32, 0, len(pixels), 0, 0, 0, 0)
    icon += pixels + bytes(side * 4)
    group = struct.pack("<HHHBBBBHHIH", 0, 1, 1, side, side, 0, 0, 1, 32, len(icon), 1)
    rva, raw = 0x1000, 0x200
    pe = bytearray(raw + 0x100 + len(icon))
    struct.pack_into("<HI", pe, 0, 0x5a4d, 0)
    struct.pack_into("<I", pe, 60, 64)
    optional_size = 240 if pe_plus else 224
    struct.pack_into("<4sHHIIIHH", pe, 64, b"PE\0\0", 0x8664 if pe_plus else 0x14c, 1, 0, 0, 0, optional_size, 0)
    struct.pack_into("<H", pe, 88, 0x20b if pe_plus else 0x10b)
    if pe_plus:
        struct.pack_into("<I", pe, 88 + 108, 3)
        struct.pack_into("<II", pe, 88 + 112 + 16, rva, len(pe) - raw)
    struct.pack_into("<8sIIII", pe, 88 + optional_size, b".assets" if pe_plus else b".rsrc",
                     len(pe) - raw, rva, len(pe) - raw, raw)

    def node(offset, *entries):
        struct.pack_into("<H", pe, raw + offset + 14, len(entries))
        for i, (key, target) in enumerate(entries):
            struct.pack_into("<II", pe, raw + offset + 16 + i * 8, key, target)

    node(0, (3, 0x80000020), (14, 0x80000040))
    node(0x20, (1, 0x80000060))
    node(0x40, (1, 0x80000080))
    node(0x60, (1041, 0xa0))
    node(0x80, (1041, 0xb0))
    struct.pack_into("<IIII", pe, raw + 0xa0, rva + 0x100, len(icon), 0, 0)
    struct.pack_into("<IIII", pe, raw + 0xb0, rva + 0xc0, len(group), 0, 0)
    pe[raw + 0xc0:raw + 0xc0 + len(group)] = group
    pe[raw + 0x100:] = icon
    return pe
