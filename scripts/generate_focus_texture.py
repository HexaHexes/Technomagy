"""Generate the original 16px placeholder spell focus (wand) texture; no imaging dependencies.

The wand runs diagonally from the bottom-left (grip) to the top-right (jewel), the orientation
Minecraft's handheld item model expects. Pass --preview DIR to also write an enlarged copy there.
"""
from pathlib import Path
import struct
import sys
import zlib

ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/technomagy/textures/item'
SIZE = 16
CLEAR = (0, 0, 0, 0)

SHAFT_LIGHT = (150, 112, 60, 255)
SHAFT_DARK = (101, 74, 40, 255)
GRIP_LIGHT = (118, 72, 46, 255)
GRIP_DARK = (82, 49, 32, 255)
GRIP_EDGE = (55, 33, 22, 255)
GOLD_LIGHT = (240, 200, 86, 255)
GOLD_DARK = (168, 124, 42, 255)
JEWEL_EDGE = (34, 92, 136, 255)
JEWEL_MID = (74, 172, 214, 255)
JEWEL_LIGHT = (164, 228, 246, 255)
SPARKLE = (255, 255, 255, 255)


def chunk(kind, data):
    """Encode one CRC-checked PNG chunk."""
    return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data))


def png(size, pixel):
    """Encode a square RGBA image from a pixel(x, y) function."""
    scanlines = b''.join(b'\0' + bytes(channel for x in range(size) for channel in pixel(x, y))
                         for y in range(size))
    data = b'\x89PNG\r\n\x1a\n'
    data += chunk(b'IHDR', struct.pack('>IIBBBBB', size, size, 8, 6, 0, 0, 0))
    data += chunk(b'IDAT', zlib.compress(scanlines))
    data += chunk(b'IEND', b'')
    return data


def wand(x, y):
    """One pixel of the wand: pommel, wrapped grip, gold band, wooden shaft, claws, and jewel."""
    diagonal = x + y
    # Faceted jewel: a diamond centered between pixels so it has an even 6px span.
    gem = abs(x - 12.5) + abs(y - 2.5)
    if gem <= 3:
        if (x, y) == (12, 1):
            return SPARKLE
        if gem == 3:
            return JEWEL_EDGE
        return JEWEL_LIGHT if diagonal < 15 else JEWEL_MID
    # Gold claws holding the jewel, just below it on the shaft line.
    if 9 <= x <= 10 and diagonal in (15, 16):
        return GOLD_LIGHT if diagonal == 15 else GOLD_DARK
    # Wooden shaft, two pixels thick: lit upper-left edge, shaded lower-right edge.
    if 5 <= x <= 8 and diagonal in (15, 16):
        return SHAFT_LIGHT if diagonal == 15 else SHAFT_DARK
    # Gold band between grip and shaft, one pixel wider than the shaft on each side.
    if x == 4 and 14 <= diagonal <= 17:
        return GOLD_LIGHT if diagonal <= 15 else GOLD_DARK
    # Gold pommel capping the end of the grip.
    if (x, y) in ((0, 14), (0, 15), (1, 15)):
        return GOLD_DARK if (x, y) == (1, 15) else GOLD_LIGHT
    # Wider leather-wrapped grip: four pixels across, with diagonal wrap stripes.
    if 1 <= x <= 3 and 14 <= diagonal <= 17:
        if diagonal in (14, 17):
            return GRIP_EDGE
        return GRIP_LIGHT if (x - y) % 4 in (0, 1) else GRIP_DARK
    return CLEAR


def preview(path, scale=16):
    """Write an enlarged copy over a checkerboard, for reviewing the pixel art."""
    def pixel(x, y):
        color = wand(x // scale, y // scale)
        if color[3]:
            return color
        light = (x // (scale // 2) + y // (scale // 2)) % 2
        return (205, 205, 205, 255) if light else (160, 160, 160, 255)
    path.write_bytes(png(SIZE * scale, pixel))


if __name__ == '__main__':
    ROOT.mkdir(parents=True, exist_ok=True)
    (ROOT / 'spell_focus.png').write_bytes(png(SIZE, wand))
    if len(sys.argv) == 3 and sys.argv[1] == '--preview':
        preview(Path(sys.argv[2]) / 'spell_focus_preview.png')
