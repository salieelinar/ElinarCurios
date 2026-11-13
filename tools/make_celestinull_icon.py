import random
from PIL import Image

SIZE = 16
random.seed(19082)  # 固定种子,保证可复现

img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 255))
px = img.load()

cx = cy = (SIZE - 1) / 2.0
maxd = (cx ** 2 + cy ** 2) ** 0.5

for y in range(SIZE):
    for x in range(SIZE):
        d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
        t = max(0.0, 1.0 - d / maxd)          # 中心 1 -> 边缘 0
        # 深紫罗兰底:中心亮紫,边缘近黑
        r = int(28 + 92 * t ** 1.6)
        g = int(10 + 34 * t ** 1.6)
        b = int(48 + 130 * t ** 1.6)
        # 轻微噪声,避免纯色死板
        n = random.randint(-6, 6)
        r, g, b = (max(0, min(255, c + n)) for c in (r, g, b))
        px[x, y] = (r, g, b, 255)

# 星点:中心星团更密
stars = []
for _ in range(26):
    x = random.randrange(SIZE)
    y = random.randrange(SIZE)
    d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
    if d < 5.0 and random.random() < 0.75:
        stars.append((x, y, True))
    elif random.random() < 0.35:
        stars.append((x, y, False))

for x, y, bright in stars:
    base = px[x, y]
    add = 150 if bright else 80
    px[x, y] = (
        min(255, base[0] + add),
        min(255, base[1] + add),
        min(255, base[2] + add),
        255,
    )

# 1px 深色外框,和 GT 方块贴图的观感一致
frame = (14, 6, 24, 255)
for i in range(SIZE):
    px[i, 0] = frame
    px[i, SIZE - 1] = frame
    px[0, i] = frame
    px[SIZE - 1, i] = frame

out = r"D:\Agent\GTNH\ElinarCurios\src\main\resources\assets\elinarcurios\textures\blocks\celestinullVoidNode.png"
img.save(out)
print("saved:", out, img.size)
