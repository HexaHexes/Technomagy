#!/usr/bin/env python3
"""Generate the elemental crystal textures.

Every texture is drawn from the same per-flavor palette:

* item/<flavor>_crystal.png                   - the harvested crystal: an octagonal rod with
                                                flattened octagonal-pyramid ends, ray traced
                                                at pixel scale so each facet gets flat shading
* block/<flavor>_crystal_cluster_stage<N>.png  - cross sprites for the four growth stages,
                                                built from the same rods
* block/<flavor>_crystal_block.png             - tileable faceted crystal
* block/<flavor>_budding_crystal.png           - the crystal block with empty bud sockets

Usage:
    python3 scripts/generate_crystal_textures.py [--preview PATH]

Output is deterministic. Pure Python, no third-party packages.
"""
import argparse
import colorsys
import math
import random
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TEXTURES = ROOT / 'src/main/resources/assets/technomagy/textures'

# hue (degrees), saturation, darkest value, brightest value, highlight hue, layout seed
FLAVORS = {
    'fire':  dict(hue=12,  sat=0.86, floor=0.30, val=1.00, glint=50,  seed=11),
    'earth': dict(hue=108, sat=0.64, floor=0.18, val=0.86, glint=74,  seed=23),
    'air':   dict(hue=188, sat=0.30, floor=0.42, val=1.00, glint=165, seed=37),
    'water': dict(hue=220, sat=0.74, floor=0.22, val=0.98, glint=190, seed=41),
    'light': dict(hue=48,  sat=0.40, floor=0.58, val=1.00, glint=58,  seed=53),
    'dark':  dict(hue=270, sat=0.58, floor=0.08, val=0.56, glint=300, seed=67),
}

SHADES = 7  # 0 = outline ... 5 = highlight, 6 = glint


def palette(f):
    """Seven shades from outline to glint, shifting toward the glint hue as they brighten."""
    out = []
    for i in range(SHADES):
        t = i / (SHADES - 1)
        hue = f['hue'] + (f['glint'] - f['hue']) * max(0.0, t - 0.45) * 1.6
        sat = f['sat'] * (1.0 - 0.55 * t * t)
        top = f['val'] if i < SHADES - 1 else max(f['val'], 0.85)
        val = f['floor'] + (top - f['floor']) * t ** 0.85
        r, g, b = colorsys.hsv_to_rgb((hue % 360) / 360, min(sat, 1.0), min(val, 1.0))
        out.append((round(r * 255), round(g * 255), round(b * 255), 255))
    return out


# --- ray traced crystal rods -------------------------------------------------------------

def norm(v):
    m = math.sqrt(sum(c * c for c in v))
    return tuple(c / m for c in v)


def sub(a, b): return tuple(x - y for x, y in zip(a, b))
def add(a, b): return tuple(x + y for x, y in zip(a, b))
def mul(a, s): return tuple(x * s for x in a)
def dot(a, b): return sum(x * y for x, y in zip(a, b))
def cross(a, b): return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def rod(base, angle, length, apothem, cap=0.55, tilt=0.0):
    """A convex octagonal prism with pyramid caps, as a list of (normal, offset) half-spaces.

    base: (x, y) of the bottom end's center in pixels (y up); angle: degrees from vertical,
    positive leans right; tilt: how far the far end leans toward the viewer (z per unit of
    length), so its cap facets show; length: prism length; apothem: centre-to-face distance;
    cap: pyramid height as a fraction of the apothem (small = flattened).
    """
    a = math.radians(angle)
    axis = norm((math.sin(a), math.cos(a), tilt))
    u = norm(cross(axis, (0.0, 0.0, 1.0)))
    w = cross(u, axis)
    if w[2] < 0:
        u, w = mul(u, -1), mul(w, -1)
    radius = apothem / math.cos(math.pi / 8)
    start = (base[0], base[1], 0.0)
    end = add(start, mul(axis, length))
    ring = []
    for k in range(8):
        phi = math.pi / 8 + k * math.pi / 4  # puts a flat face toward the viewer
        ring.append(add(mul(u, radius * math.cos(phi)), mul(w, radius * math.sin(phi))))
    planes = []
    for k in range(8):  # side faces
        mid = norm(add(ring[k], ring[(k + 1) % 8]))
        planes.append((mid, dot(mid, add(start, mul(mid, apothem)))))
    centre = add(start, mul(axis, length / 2))
    for origin, direction in ((end, 1), (start, -1)):  # pyramid caps
        apex = add(origin, mul(axis, direction * apothem * cap))
        for k in range(8):
            p0, p1 = add(origin, ring[k]), add(origin, ring[(k + 1) % 8])
            n = norm(cross(sub(p0, apex), sub(p1, apex)))
            if dot(n, sub(apex, centre)) < 0:
                n = mul(n, -1)
            planes.append((n, dot(n, apex)))
    return planes


def hit(planes, x, y):
    """Front-most hit of an orthographic ray looking down -z, as (depth, normal) or None."""
    t_enter, t_exit, face = -1e9, 1e9, None
    for n, c in planes:
        # point (x, y, z0 - t): n.p <= c  ->  n.xy + n.z * (z0 - t) <= c, with z0 = 100
        base = n[0] * x + n[1] * y + n[2] * 100.0 - c
        rate = -n[2]
        if abs(rate) < 1e-9:
            if base > 0:
                return None
            continue
        t = -base / rate
        if rate < 0:
            if t > t_enter:
                t_enter, face = t, n
        elif t < t_exit:
            t_exit = t
    return (t_enter, face) if face is not None and t_enter <= t_exit else None


LIGHT = norm((-0.55, 0.75, 0.9))


def shade_of(normal):
    lit = max(0.0, dot(normal, LIGHT))
    return max(1, min(5, round(0.6 + 4.6 * lit ** 1.3)))


def render(solids, size=16, samples=4):
    """Rasterise rods to a grid of shade indices (None = empty), then outline the silhouette."""
    grid = [[None] * size for _ in range(size)]
    for py in range(size):
        for px in range(size):
            votes = {}
            for sy in range(samples):
                for sx in range(samples):
                    x = px + (sx + 0.5) / samples
                    y = size - (py + (sy + 0.5) / samples)
                    best = None
                    for planes in solids:
                        h = hit(planes, x, y)
                        if h and (best is None or h[0] < best[0]):
                            best = h
                    if best:
                        s = shade_of(best[1])
                        votes[s] = votes.get(s, 0) + 1
            if sum(votes.values()) * 2 >= samples * samples:
                grid[py][px] = max(votes, key=votes.get)
    outline = [row[:] for row in grid]
    for py in range(size):
        for px in range(size):
            if grid[py][px] is None:
                continue
            edge = [grid[py + dy][px + dx] is None if 0 <= py + dy < size and 0 <= px + dx < size else False
                    for dx, dy in ((1, 0), (0, 1), (-1, 0), (0, -1))]
            if edge[0] or edge[1]:      # lower/right edges: dark outline
                outline[py][px] = 0
    return outline


def glint(grid, points):
    for x, y in points:
        if grid[y][x] is not None and grid[y][x] >= 3:
            grid[y][x] = 6
    return grid


def crystal_item():
    # Lies bottom-left to top-right like vanilla shards, top end tipped toward the viewer.
    return render([rod((4.9, 4.9), 45, 8.6, 2.35, cap=1.5)])


CLUSTER_STAGES = [
    [((8, -0.5), 0, 2.6, 2.6)],
    [((8, -0.8), 0, 4.4, 2.3), ((5.2, -0.6), -45, 2.6, 1.6), ((10.8, -0.6), 45, 2.6, 1.6)],
    [((8, -0.8), 0, 7.6, 2.4), ((5.2, -0.6), -40, 4.4, 1.8), ((10.8, -0.6), 40, 4.4, 1.8)],
    [((4.6, -0.6), -58, 4.0, 1.6), ((11.4, -0.6), 58, 4.0, 1.6),
     ((5.6, -0.6), -30, 7.0, 1.9), ((10.4, -0.6), 30, 7.0, 1.9),
     ((8, -0.8), 0, 11.6, 2.5)],
]


def cluster(stage):
    g = render([rod(*r, cap=0.85, tilt=0.12) for r in CLUSTER_STAGES[stage]])
    top = next(y for y in range(16) if any(v is not None for v in g[y]))
    return glint(g, [(7, top + 2)] if stage >= 2 else [])


# --- tileable faceted block textures ------------------------------------------------------

def facets(seed):
    """Low-contrast mottling from tileable waves, crossed by short diagonal facet streaks:
    a lit line with a shadow under it, and the odd glint."""
    rnd = random.Random(seed)
    waves = []
    for _ in range(5):
        fx, fy = rnd.choice([(1, 1), (1, -1), (2, 1), (1, 2), (2, -1), (0, 1), (1, 0)])
        waves.append((fx, fy, rnd.uniform(0, 2 * math.pi), rnd.uniform(0.6, 1.0)))
    grid = [[0] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            v = sum(a * math.sin(2 * math.pi * (fx * x + fy * y) / 16 + ph) for fx, fy, ph, a in waves)
            v += rnd.uniform(-0.35, 0.35)
            grid[y][x] = 2 if v < -1.2 else 4 if v > 1.3 else 3
    for _ in range(6):
        x, y = rnd.randrange(16), rnd.randrange(16)
        dx = rnd.choice([1, -1])
        n = rnd.randint(2, 3)
        for i in range(n):
            px, py = (x + dx * i) % 16, (y + i) % 16
            grid[(py - 1) % 16][px] = max(grid[(py - 1) % 16][px], 4)
            grid[(py + 1) % 16][px] = min(grid[(py + 1) % 16][px], 2)
        for i in range(n):
            grid[(y + i) % 16][(x + dx * i) % 16] = 5
        if n == 3 and rnd.random() < 0.6:
            grid[(y + 1) % 16][(x + dx) % 16] = 6
    return grid


SOCKETS = [(4, 3), (12, 6), (5, 12)]


def budding(seed):
    """The crystal block with sockets where buds start: a dark diamond-shaped pit, shadowed
    on its upper-left inner wall, with a lit rim on the lower-right and a seed glint inside."""
    grid = facets(seed)
    for cx, cy in SOCKETS:
        for dy in range(-2, 3):
            for dx in range(-2, 3):
                d = abs(dx) + abs(dy)
                px, py = (cx + dx) % 16, (cy + dy) % 16
                if d <= 1:
                    grid[py][px] = 0 if dx + dy < 0 else 1
                elif d == 2:
                    grid[py][px] = 1 if dx + dy < 0 else 2 if dx + dy == 0 else 5
        grid[cy][cx] = 1
        grid[(cy + 1) % 16][(cx + 1) % 16] = 4
    return grid


# --- output ----------------------------------------------------------------------------------

def to_pixels(grid, pal):
    return [[(0, 0, 0, 0) if v is None else pal[v] for v in row] for row in grid]


def write_png(path, pixels):
    h, w = len(pixels), len(pixels[0])
    raw = b''.join(b'\x00' + bytes(c for p in row for c in p) for row in pixels)

    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data) & 0xffffffff)

    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(b'\x89PNG\r\n\x1a\n'
                     + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0))
                     + chunk(b'IDAT', zlib.compress(raw, 9))
                     + chunk(b'IEND', b''))


def preview(path, rows, scale=10):
    """One row per flavor, enlarged over a checkerboard, block textures shown 2x2 to check tiling."""
    gap = 6
    widths = [len(img[0]) * scale for img in rows[0]]
    w = sum(widths) + gap * (len(widths) + 1)
    hgt = max(len(img) for img in rows[0]) * scale
    h = len(rows) * (hgt + gap) + gap
    out = [[(40, 40, 48, 255)] * w for _ in range(h)]
    for r, imgs in enumerate(rows):
        ox, oy = gap, gap + r * (hgt + gap)
        for img in imgs:
            for y in range(len(img) * scale):
                for x in range(len(img[0]) * scale):
                    cr, cg, cb, ca = img[y // scale][x // scale]
                    chk = 92 if ((x // 5) + (y // 5)) % 2 else 76
                    out[oy + y][ox + x] = tuple(int((c * ca + chk * (255 - ca)) / 255) for c in (cr, cg, cb)) + (255,)
            ox += len(img[0]) * scale + gap
    write_png(Path(path), out)


def tile2(pixels):
    return [row + row for row in pixels] * 2


def main():
    parser = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    parser.add_argument('--preview', help='also write an enlarged preview sheet to this path')
    args = parser.parse_args()
    clusters = [cluster(s) for s in range(4)]
    item = crystal_item()
    rows = []
    for name, f in FLAVORS.items():
        pal = palette(f)
        textures = {
            f'item/{name}_crystal': item,
            f'block/{name}_crystal_block': facets(f['seed']),
            f'block/{name}_budding_crystal': budding(f['seed']),
        }
        for s, g in enumerate(clusters):
            textures[f'block/{name}_crystal_cluster_stage{s}'] = g
        images = {k: to_pixels(g, pal) for k, g in textures.items()}
        for k, px in images.items():
            write_png(TEXTURES / f'{k}.png', px)
        rows.append([images[f'item/{name}_crystal']]
                    + [images[f'block/{name}_crystal_cluster_stage{s}'] for s in range(4)]
                    + [tile2(images[f'block/{name}_crystal_block']), tile2(images[f'block/{name}_budding_crystal'])])
    if args.preview:
        preview(args.preview, rows)


if __name__ == '__main__':
    main()
