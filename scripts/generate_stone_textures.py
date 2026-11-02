#!/usr/bin/env python3
"""Generate the flavored stone textures.

* block/<flavor>_stone.png  - smooth stone tinted toward the flavor's crystal: a flat face with
                              faint horizontal streaks inside a darker one-pixel rim

Dark stone is plain dark gray; it is also the base of the machine pieces (node director, prism).
Each stone's slab and wall use the same texture.

Usage:
    python3 scripts/generate_stone_textures.py [--preview PATH]

Output is deterministic. Pure Python, no third-party packages.
"""
import argparse
import colorsys
import random
from pathlib import Path

from generate_crystal_textures import preview, tile2, write_png

ROOT = Path(__file__).resolve().parent.parent
TEXTURES = ROOT / 'src/main/resources/assets/technomagy/textures/block'

# average face colour as hue (degrees), saturation, value: the crystal hues, muted toward stone
STONES = {
    'fire':  dict(hue=12,  sat=0.42, val=0.55),
    'earth': dict(hue=108, sat=0.28, val=0.46),
    'air':   dict(hue=188, sat=0.14, val=0.73),
    'water': dict(hue=220, sat=0.34, val=0.51),
    'light': dict(hue=48,  sat=0.18, val=0.82),
    'dark':  dict(hue=0,   sat=0.00, val=0x55 / 255),
}
FACE = (0.94, 1.05)   # face brightness range, relative to the stone's colour
RIM = (0.70, 0.86)    # rim brightness range
SEED = 7


def smooth_stone(color, seed):
    rng = random.Random(seed)
    pixels = []
    for y in range(16):
        row = []
        x = 0
        while x < 16:
            # short horizontal runs of one shade give smooth stone's streaks
            run = rng.randint(1, 4)
            on_rim_row = y in (0, 15)
            shade = rng.uniform(*(RIM if on_rim_row else FACE))
            for _ in range(min(run, 16 - x)):
                s = rng.uniform(*RIM) if x in (0, 15) and not on_rim_row else shade
                row.append(tuple(max(0, min(255, round(c * s))) for c in color) + (255,))
                x += 1
        pixels.append(row)
    return pixels


def rgb(stone):
    r, g, b = colorsys.hsv_to_rgb(stone['hue'] / 360, stone['sat'], stone['val'])
    return r * 255, g * 255, b * 255


def main():
    parser = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    parser.add_argument('--preview', help='also write an enlarged preview sheet to this path')
    args = parser.parse_args()
    row = []
    for flavor, stone in STONES.items():
        pixels = smooth_stone(rgb(stone), SEED)
        write_png(TEXTURES / f'{flavor}_stone.png', pixels)
        row.append(tile2(pixels))
    if args.preview:
        preview(Path(args.preview), [row])


if __name__ == '__main__':
    main()
