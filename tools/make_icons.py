#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Generate item icons for Elinar Curios.

1) 15 voltage tiers x 4 charge stages = 60 battery icons. Charge core color
   follows the GT tier palette (GTValues.TIER_COLORS, chat colors approximated
   to RGB); core HEIGHT encodes charge stage. Shell stays uniform deep purple so
   the tier is told apart by the core color.
2) 2 icons for the Tianshu Thermostatic Core (reactorCore / reactorCoreHot):
   a lapis-blue heat-sink block with gold trim when cold, the same block with the
   core glowing hot once the stored heat passes 50%.
"""
from PIL import Image, ImageDraw
import os

OUT = r"D:/Agent/GTNH/ElinarCurios/src/main/resources/assets/elinarcurios/textures/items"
os.makedirs(OUT, exist_ok=True)

# tier -> core main color (approx of GT chat tier colors, tuned for distinction)
TIER_COLORS = [
    (0xFF, 0x55, 0x55),  # 0  ULV red
    (0x3C, 0xB3, 0x3C),  # 1  LV dark green (brightened)
    (0xFF, 0xAA, 0x00),  # 2  MV gold
    (0xFF, 0xE0, 0x4D),  # 3  HV yellow
    (0x8A, 0x8A, 0x8A),  # 4  EV dark gray
    (0x5F, 0x5F, 0xFF),  # 5  IV blue
    (0xE6, 0x5C, 0xE6),  # 6  LuV light purple
    (0x3E, 0xD6, 0xD6),  # 7  ZPM aqua
    (0x1E, 0x8A, 0x1E),  # 8  UV dark green (deep)
    (0xB0, 0x24, 0x24),  # 9  UHV dark red
    (0xA8, 0x3C, 0xE0),  # 10 UEV dark purple
    (0x30, 0x30, 0xE0),  # 11 UIV dark blue
    (0xFF, 0x33, 0x33),  # 12 UMV red
    (0x88, 0x14, 0x14),  # 13 UXV darkest red
    (0xED, 0xED, 0xFF),  # 14 MAX white
]

# charge stage -> core height (strictly inside shell cavity)
STAGES = [("wb_", (9, 6, 3, 1))]  # heights per stage 3/2/1/0

SHELL = (0x2B, 0x1A, 0x47)
RIM = (0x66, 0x44, 0xA0)
CAVITY = (0x14, 0x0C, 0x24)
METAL = (0xC9, 0xCC, 0xD4)
METAL_DARK = (0x8E, 0x92, 0x9C)


def lighten(c, f):
    return tuple(min(255, int(v + (255 - v) * f)) for v in c)


def darken(c, f):
    return tuple(int(v * f) for v in c)


def draw_battery(core_h, color):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    # top terminal nub
    d.rounded_rectangle([6, 0, 9, 2], radius=1, fill=METAL)
    d.rectangle([6, 0, 9, 0], fill=METAL_DARK)

    # shell
    d.rounded_rectangle([2, 2, 13, 14], radius=2, fill=SHELL, outline=RIM)
    d.rectangle([3, 3, 12, 13], fill=CAVITY)

    # charge core
    glint = lighten(color, 0.75)
    shade = darken(color, 0.55)
    top = 13 - core_h
    d.rectangle([4, top, 11, 12], fill=color)
    if core_h >= 2:
        d.rectangle([4, top, 11, top], fill=glint)
        d.rectangle([4, top + 1, 4, 12], fill=(255, 255, 255, 36))
    if core_h >= 4:
        d.rectangle([4, 12, 11, 12], fill=(0, 0, 0, 46))
    d.rectangle([4, 12, 11, 12], fill=shade)  # bottom of core in deep tone

    # inner rim depth
    d.rectangle([3, 3, 12, 3], fill=(255, 255, 255, 14))
    d.rectangle([3, 13, 12, 13], fill=(0, 0, 0, 40))
    return img


n = 0
for tier, color in enumerate(TIER_COLORS):
    # stage 0=空(最矮)… stage 3=满(最高),与代码 icons[t][0..3] 语义一致
    for stage, h in enumerate((1, 3, 6, 9)):
        name = "wb_%d_%d" % (tier, stage)
        draw_battery(h, color).save(os.path.join(OUT, name + ".png"))
        n += 1
print("saved", n, "icons")


# ---------------------------------------------------------------------------
# 天枢·恒温核心:冷态 / 高热两张贴图(静态,不做动画渐变)
# 造型 = 青金石蓝的散热片方块 + 金色镶边,高热态核心转为炽橙
# ---------------------------------------------------------------------------
CORE_COLD = (0x28, 0x4B, 0x8C)  # 青金石蓝
CORE_COLD_LIGHT = (0x46, 0x74, 0xC8)
CORE_HOT = (0xE0, 0x50, 0x14)  # 炽橙
CORE_HOT_LIGHT = (0xFF, 0xA0, 0x30)
CORE_BODY_DARK = (0x14, 0x24, 0x46)
GOLD = (0xD8, 0xAC, 0x2A)
GOLD_DARK = (0xA8, 0x82, 0x18)


def draw_reactor_core(cold):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    base = CORE_COLD if cold else CORE_HOT
    light = CORE_COLD_LIGHT if cold else CORE_HOT_LIGHT

    # 外壳:深色基座(空出的四角留透明,方块感)
    d.rectangle([1, 1, 14, 14], fill=CORE_BODY_DARK)
    # 主体
    d.rectangle([2, 2, 13, 13], fill=base)
    # 顶部高光
    d.rectangle([2, 2, 13, 3], fill=light)
    # 底部阴影
    d.rectangle([2, 12, 13, 13], fill=darken(base, 0.65))

    # 散热鳍片:三道横向凹槽
    for y in (5, 8, 11):
        d.rectangle([3, y, 12, y], fill=darken(base, 0.55))
        d.rectangle([3, y + 1, 12, y + 1], fill=light if cold else light)

    # 金色镶边(左/右柱)与四角包边
    d.rectangle([2, 2, 2, 13], fill=GOLD)
    d.rectangle([13, 2, 13, 13], fill=GOLD)
    d.rectangle([2, 2, 13, 2], fill=GOLD)
    d.rectangle([2, 13, 13, 13], fill=GOLD_DARK)

    # 中央核心(冷=蓝白,热=白炽)
    core_glow = (0xE8, 0xF4, 0xFF) if cold else (0xFF, 0xF0, 0xC0)
    d.rectangle([6, 6, 9, 9], fill=core_glow)
    d.rectangle([7, 7, 8, 8], fill=(255, 255, 255) if not cold else (0xFF, 0xFF, 0xFF))
    if not cold:
        # 高热时核心外溢一圈暖光
        d.rectangle([5, 5, 10, 5], fill=CORE_HOT_LIGHT)
        d.rectangle([5, 10, 10, 10], fill=CORE_HOT_LIGHT)
        d.rectangle([5, 5, 5, 10], fill=CORE_HOT_LIGHT)
        d.rectangle([10, 5, 10, 10], fill=CORE_HOT_LIGHT)

    # 四角切掉一个像素,做出圆角方块观感
    for (cx, cy) in ((1, 1), (14, 1), (1, 14), (14, 14)):
        d.point((cx, cy), fill=(0, 0, 0, 0))
    return img


draw_reactor_core(True).save(os.path.join(OUT, "reactorCore.png"))
draw_reactor_core(False).save(os.path.join(OUT, "reactorCoreHot.png"))
print("saved 2 reactor core icons")
