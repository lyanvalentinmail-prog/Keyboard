#!/usr/bin/env python3
"""
Genera los PNG de respaldo del icono de lanzador para dispositivos API 24-25
(que no soportan iconos adaptativos). En API 26+ se usa mipmap-anydpi-v26 con
el VectorDrawable @drawable/ic_launcher_foreground.

Renderiza el mismo glifo del vector con supersampling 4x (antialiasing) y
escribe PNGs RGBA válidos usando solo la biblioteca estándar de Python.

Uso:
    python3 tools/generate_launcher_icons.py
"""

import os
import struct
import zlib

# Geometría del glifo en el espacio normalizado 0..108 (igual que el vector).
BG_COLOR = (26, 115, 232)       # #1A73E8
FG_COLOR = (255, 255, 255)
RING = (30.5, 40.0, 77.5, 74.0)  # x0, y0, x1, y1 (rectángulo del teclado)
RING_RADIUS = 5.5
RING_STROKE = 3.5
PILLS = [
    (40, 47, 47, 52), (50, 47, 57, 52), (60, 47, 67, 52),
    (40, 56, 47, 61), (50, 56, 57, 61), (60, 56, 67, 61),
]
SPACE_BAR = (38, 65, 70, 69)
SQUARE_CORNER_RADIUS = 0.18     # radio de la esquina del icono cuadrado legacy

SUPERSAMPLE = 4
DENSITIES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

RES_DIR = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res")


def in_rounded_rect(x, y, x0, y0, x1, y1, r):
    """true si el punto está dentro del rectángulo redondeado."""
    if x < x0 or x > x1 or y < y0 or y > y1:
        return False
    # Franjas centrales: siempre dentro.
    if (x0 + r <= x <= x1 - r) or (y0 + r <= y <= y1 - r):
        return True
    # Esquinas: distancia al centro del arco.
    cx = x0 + r if x < x0 + r else x1 - r
    cy = y0 + r if y < y0 + r else y1 - r
    return (x - cx) ** 2 + (y - cy) ** 2 <= r * r


def glyph_at(nx, ny):
    """true si el punto normalizado (0..108) pertenece al glifo blanco."""
    x0, y0, x1, y1 = RING
    s = RING_STROKE
    in_ring = in_rounded_rect(nx, ny, x0, y0, x1, y1, RING_RADIUS) and not in_rounded_rect(
        nx, ny, x0 + s, y0 + s, x1 - s, y1 - s, max(RING_RADIUS - s, 0.01)
    )
    if in_ring:
        return True
    for px0, py0, px1, py1 in PILLS:
        if px0 <= nx <= px1 and py0 <= ny <= py1:
            return True
    bx0, by0, bx1, by1 = SPACE_BAR
    return in_rounded_rect(nx, ny, bx0, by0, bx1, by1, 1.5)


def render(size, round_icon):
    """Renderiza el icono a `size` px con supersampling y devuelve bytes RGBA."""
    n = size * SUPERSAMPLE
    acc = bytearray(n * n * 4)
    center = n / 2.0
    radius = n / 2.0
    corner = size * SQUARE_CORNER_RADIUS * SUPERSAMPLE

    for py in range(n):
        for px in range(n):
            x = px + 0.5
            y = py + 0.5
            if round_icon:
                inside_bg = (x - center) ** 2 + (y - center) ** 2 <= radius ** 2
            else:
                inside_bg = in_rounded_rect(x, y, 0, 0, n, n, corner)
            if not inside_bg:
                continue  # transparente
            nx = x / n * 108.0
            ny = y / n * 108.0
            color = FG_COLOR if glyph_at(nx, ny) else BG_COLOR
            i = (py * n + px) * 4
            acc[i:i + 4] = bytes((color[0], color[1], color[2], 255))

    # Reducción por bloques KxK (media simple).
    k = SUPERSAMPLE
    out = bytearray(size * size * 4)
    for y in range(size):
        for x in range(size):
            r = g = b = a = 0
            for dy in range(k):
                row = (y * k + dy) * n
                for dx in range(k):
                    i = (row + x * k + dx) * 4
                    r += acc[i]
                    g += acc[i + 1]
                    b += acc[i + 2]
                    a += acc[i + 3]
            q = k * k
            o = (y * size + x) * 4
            out[o:o + 4] = bytes((r // q, g // q, b // q, a // q))
    return out


def write_png(path, width, height, rgba):
    """Escribe un PNG RGBA de 8 bits válido (filtro 0 por línea)."""
    raw = b"".join(
        b"\x00" + rgba[y * width * 4:(y + 1) * width * 4] for y in range(height)
    )

    def chunk(tag, data):
        return (
            struct.pack(">I", len(data)) + tag + data
            + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
        )

    png = (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
        + chunk(b"IDAT", zlib.compress(raw, 9))
        + chunk(b"IEND", b"")
    )
    with open(path, "wb") as fh:
        fh.write(png)


def main():
    for folder, size in DENSITIES.items():
        target_dir = os.path.join(RES_DIR, folder)
        os.makedirs(target_dir, exist_ok=True)
        for name, round_icon in (("ic_launcher", False), ("ic_launcher_round", True)):
            rgba = render(size, round_icon)
            path = os.path.join(target_dir, name + ".png")
            write_png(path, size, size, rgba)
            print(f"  {path} ({size}x{size})")


if __name__ == "__main__":
    main()
