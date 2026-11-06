"""Generate original geometric 18px status glyphs; no copied artwork or imaging dependencies."""
from pathlib import Path
import struct
import zlib

ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/technomagy/textures/mob_effect'


def chunk(kind, data):
    """Encode one CRC-checked PNG chunk."""
    return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data))


def write(name, pixel):
    """Write an 18-by-18 RGBA glyph with transparent surroundings."""
    scanlines = b''.join(b'\0' + bytes(channel for x in range(18) for channel in pixel(x, y))
                         for y in range(18))
    data = b'\x89PNG\r\n\x1a\n'
    data += chunk(b'IHDR', struct.pack('>IIBBBBB', 18, 18, 8, 6, 0, 0, 0))
    data += chunk(b'IDAT', zlib.compress(scanlines))
    data += chunk(b'IEND', b'')
    (ROOT / (name + '.png')).write_bytes(data)


def shroud(x, y):
    """A pale crescent and a small star indicate concealed presence."""
    outer = (x - 8) ** 2 + (y - 9) ** 2 <= 36
    cutout = (x - 11) ** 2 + (y - 7) ** 2 <= 30
    star = (x == 14 and 1 <= y <= 5) or (y == 3 and 12 <= x <= 16)
    return (210, 184, 245, 255) if outer and not cutout or star else (0, 0, 0, 0)


def leach(x, y):
    """A cracked green shield indicates weakened armor."""
    width = 6 if y <= 10 else max(0, 16 - y)
    shield = 3 <= y <= 15 and abs(x - 8.5) <= width
    crack = abs(x - (6 + y * 0.3)) < 0.8
    return (87, 189, 121, 255) if shield and not crack else (0, 0, 0, 0)


if __name__ == '__main__':
    ROOT.mkdir(parents=True, exist_ok=True)
    write('shroud', shroud)
    write('leach', leach)
