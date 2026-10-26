#!/usr/bin/env python3
"""生成 OnlineIM 桌面端图标：应用图标、托盘图标，以及带未读红点的托盘图标。"""

import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent
TEAL = (14, 116, 144, 255)
WHITE = (255, 255, 255, 255)
RED = (220, 38, 38, 255)


def blend(dst, src):
    sr, sg, sb, sa = src
    if sa <= 0:
        return dst
    if sa >= 255:
        return src
    dr, dg, db, da = dst
    out_a = sa + da * (255 - sa) // 255
    if out_a == 0:
        return (0, 0, 0, 0)

    def channel(s, d):
        return (s * sa + d * da * (255 - sa) // 255) // out_a

    return (channel(sr, dr), channel(sg, dg), channel(sb, db), out_a)


def cover(dist, radius):
    if radius <= 0:
        return 0
    delta = radius - dist
    if delta >= 0.5:
        return 255
    if delta <= -0.5:
        return 0
    return int((delta + 0.5) * 255)


def color_at(x, y, size, unread):
    cx = cy = size / 2
    dx = x - cx
    dy = y - cy
    dist = (dx * dx + dy * dy) ** 0.5
    pixel = (0, 0, 0, 0)
    background = cover(dist, size * 0.46)
    if background:
        pixel = blend(pixel, (TEAL[0], TEAL[1], TEAL[2], background))

    bubble_w = size * 0.46
    bubble_h = size * 0.32
    left = cx - bubble_w / 2
    top = cy - bubble_h / 2 - size * 0.03
    radius = size * 0.08
    bx = min(max(x, left + radius), left + bubble_w - radius)
    by = min(max(y, top + radius), top + bubble_h - radius)
    # 圆角矩形：到裁剪后中心的距离再减去半径，相当于 SDF
    corner_dx = max(abs(x - (left + bubble_w / 2)) - (bubble_w / 2 - radius), 0)
    corner_dy = max(abs(y - (top + bubble_h / 2)) - (bubble_h / 2 - radius), 0)
    bubble = cover((corner_dx * corner_dx + corner_dy * corner_dy) ** 0.5, radius)
    if bubble:
        pixel = blend(pixel, (WHITE[0], WHITE[1], WHITE[2], bubble))

    tail_cx = left + size * 0.08
    tail_cy = top + bubble_h - size * 0.01
    tail = cover(((x - tail_cx) ** 2 + (y - tail_cy) ** 2) ** 0.5, size * 0.07)
    if tail and y >= top + bubble_h * 0.55:
        pixel = blend(pixel, WHITE)

    if unread:
        badge_cx = size * 0.74
        badge_cy = size * 0.27
        badge_dist = ((x - badge_cx) ** 2 + (y - badge_cy) ** 2) ** 0.5
        ring = cover(badge_dist, size * 0.145)
        if ring:
            pixel = blend(pixel, (255, 255, 255, ring))
        dot = cover(badge_dist, size * 0.105)
        if dot:
            pixel = blend(pixel, (RED[0], RED[1], RED[2], dot))
    return pixel


def render(size, unread, samples=4):
    rows = bytearray()
    for py in range(size):
        rows.append(0)
        for px in range(size):
            acc = [0, 0, 0, 0]
            for sy in range(samples):
                for sx in range(samples):
                    x = px + (sx + 0.5) / samples
                    y = py + (sy + 0.5) / samples
                    color = color_at(x, y, size, unread)
                    for index, channel in enumerate(color):
                        acc[index] += channel
            count = samples * samples
            rows.extend(channel // count for channel in acc)
    return bytes(rows)


def chunk(tag, data):
    return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)


def png_bytes(size, unread):
    ihdr = struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0)
    raw = render(size, unread)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")


def write_png(path, size, unread):
    path.write_bytes(png_bytes(size, unread))


def write_ico(path, sizes):
    images = [(size, png_bytes(size, False)) for size in sizes]
    header = struct.pack("<HHH", 0, 1, len(images))
    entries = b""
    payload = b""
    offset = 6 + 16 * len(images)
    for size, data in images:
        dimension = 0 if size >= 256 else size
        entries += struct.pack("<BBBBHHII", dimension, dimension, 0, 0, 1, 32, len(data), offset)
        payload += data
        offset += len(data)
    path.write_bytes(header + entries + payload)


def write_icns(path):
    # icns 图标类型：icp5=32, ic11=32@2x, ic07=128, ic08=256, ic09=512
    typed = [
        (b"icp5", png_bytes(32, False)),
        (b"ic11", png_bytes(64, False)),
        (b"ic07", png_bytes(128, False)),
        (b"ic08", png_bytes(256, False)),
        (b"ic09", png_bytes(512, False)),
    ]
    body = b""
    for kind, data in typed:
        body += kind + struct.pack(">I", len(data) + 8) + data
    path.write_bytes(b"icns" + struct.pack(">I", len(body) + 8) + body)


def main():
    write_png(ROOT / "32x32.png", 32, False)
    write_png(ROOT / "128x128.png", 128, False)
    write_png(ROOT / "128x128@2x.png", 256, False)
    write_png(ROOT / "icon.png", 512, False)
    write_png(ROOT / "tray.png", 64, False)
    write_png(ROOT / "icon-unread.png", 64, True)
    write_ico(ROOT / "icon.ico", [16, 24, 32, 48, 64, 128, 256])
    write_icns(ROOT / "icon.icns")
    print("icons written to", ROOT)


if __name__ == "__main__":
    main()
