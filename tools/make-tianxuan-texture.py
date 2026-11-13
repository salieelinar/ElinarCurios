#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Generate the item texture for the Tianxuan Thermostatic Core (tianxuanCore.name.png).

Static 16x16 icon, deliberately a sibling of the Tianshu core texture (reactorCore.png):
violet body + silver trim + a gold ring around a locked white core, so the two read as a family in
NEI while staying distinguishable at 16x16 (per the project's static-texture preference - no
animated gradients).

The name intentionally ends in ".name.png": 1.7.10's Item.setTextureName() appends ".name" itself,
so this is what `setTextureName("elinarcurios:tianxuanCore")` resolves to.

Usage: python tools/make-tianxuan-texture.py
"""
import os

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "elinarcurios", "textures", "items")
os.makedirs(OUT, exist_ok=True)

BODY_DARK = (0x22, 0x14, 0x3A)
VIOLET = (0x4A, 0x2C, 0x7A)
VIOLET_LIGHT = (0x6E, 0x48, 0xB0)
SILVER = (0xC8, 0xCC, 0xD8)
SILVER_DARK = (0x88, 0x8C, 0x9A)
GOLD = (0xD8, 0xAC, 0x2A)
GLOW = (0xEC, 0xE4, 0xFF)


def darken(color, factor):
    return tuple(int(v * factor) for v in color)


def make_texture():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    # dark backing so the item reads as a solid block on any GUI background
    draw.rectangle([1, 1, 14, 14], fill=BODY_DARK)
    # violet body
    draw.rectangle([2, 2, 13, 13], fill=VIOLET)
    draw.rectangle([2, 2, 13, 3], fill=VIOLET_LIGHT)
    draw.rectangle([2, 12, 13, 13], fill=darken(VIOLET, 0.65))
    # silver trim (the family resemblance to the Tianshu core's gold trim, in silver)
    draw.rectangle([2, 2, 2, 13], fill=SILVER)
    draw.rectangle([13, 2, 13, 13], fill=SILVER)
    draw.rectangle([2, 2, 13, 2], fill=SILVER)
    draw.rectangle([2, 13, 13, 13], fill=SILVER_DARK)
    # corner rivets
    for xy in ((3, 3), (12, 3), (3, 12), (12, 12)):
        draw.point(xy, fill=SILVER_DARK)
    # locked core: gold ring, white core
    draw.rectangle([6, 6, 9, 9], fill=GOLD)
    draw.rectangle([7, 7, 8, 8], fill=GLOW)
    draw.point((7, 7), fill=(255, 255, 255))
    # rounded corners
    for xy in ((1, 1), (14, 1), (1, 14), (14, 14)):
        draw.point(xy, fill=(0, 0, 0, 0))
    return img


target = os.path.join(OUT, "tianxuanCore.name.png")
make_texture().save(target)
print("saved", target)
