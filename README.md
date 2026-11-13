# Elinar Curios — 埃利纳之珍奇 (Elinar Curios)

A Minecraft 1.7.10 (GTNH toolchain) mod (formerly *Infinithpackbag*) that adds the **Infinite Backpack**:
a portable backpack with the exact storage model of Avaritiaddons' **Infinity Chest** —
**243 slots, each holding up to 2,147,483,647 (Integer.MAX_VALUE) items** —
that can be **worn on your back** and opened with a **hotkey**.

**Fully standalone: no mod dependencies** (AdventureBackpack2 not required, and no
AdventureBackpack2 classes are referenced at runtime).

> **2026-08-16 改名**:mod 由 *Infinithpackbag* 更名为 **Elinar Curios**(modId `elinarcurios`),
> 并同步升级到 GTNH 官方现代工具链(Gradle 9.4.0 / JDK 25 / gtnhsettingsconvention 2.0.29)。
> 同日新增 **Peace Totem(和平图腾)** 与配套服务端修复。
> 2026-10-18 **v1.2.13**:并入**高级装配线**,并补充两个**样板镜像总成**的说明。
> 历史版本见 git 标签与 DEV_GUIDE.md 顶部「当前版本状态」。

## Features

- 243-slot storage with per-slot Integer.MAX_VALUE capacity, auto-merging stacks
  (long-arithmetic overflow-safe), NBT persistence via a custom `intCount` tag.
- Huge 500x276 Infinity-Chest style GUI with K/M/B stack rendering (1.2K / 3.4M / 1B).
- Custom server-authoritative network protocol (4 packets) so stacks > 32767 sync correctly.
- Wear it on your back (built-in player property):
  - Rendered on the player's back with the **Dragon backpack skin** (texture bundled).
  - Grants **Night Vision** while worn (toggleable in the backpack GUI).
- Equip/Unequip button inside the backpack GUI.
- Registered hotkey **B** (configurable in Controls) opens the worn or held backpack.
- Drop on death, forbidden-dimension safety (dim 55), no crafting recipe yet
  (creative tab only).
- **Peace Totem(和平图腾)**: placeable totem that blocks natural hostile-mob
  spawning within a configurable ellipsoid (default 64 horizontal / 32 vertical
  blocks). Spawners, spawn eggs and /summon keep working. Fully standalone,
  no mod dependencies (mechanism re-implemented from scratch). Looks like the
  classic Magnum Torch (flame head + staff), with a deep-purple staff matching
  the Infinite Backpack.

## GTNH additions (since v1.2.4)

- **Celestial Void Energy Node** — a single-block GT machine that buffers cable EU
  and flushes it into the wireless energy grid.
- **Pattern Assembly Mirrors (Item / Fluid)** — place one on an Assembly Line's
  input-bus / input-hatch position, bind it to an existing pattern assembly
  (vanilla CRIB or GTNL's 360-slot hatch) with a Data Stick, and it feeds the
  Assembly Line "slot N" directly from the pattern buffer (zero-copy, no pipes).
  See `DEV_GUIDE.md` §5.24.
- **High-Tier Assembly Line (高级装配线)** — a 1:1 structure clone of the vanilla
  Assembly Line that accepts Multi-Amp / Laser energy hatches and a GTNL Parallel
  Controller Hatch; the two-LuV-hatch ZPM voltage boost is preserved. See
  `DEV_GUIDE.md` §5.25.
- **Fresnel Lens** — even neutron irradiation of every breeder cell in a reactor.
- **ME output fast flush** — GT's ME output bus/hatch only pushes to the network every
  2 s (a hard-coded floor, not a tunable default). While a multiblock is working **and**
  the network has a **player-ordered** crafting job, ElinarCurios flushes that hatch
  every tick instead; stock-keeper (AE2FC level maintainer) orders are ignored.
  `/Elinar meflush` reports status. See `DEV_GUIDE.md` §5.34.

## Building

JDK 25 required to run Gradle (GTNH modern toolchain; the mod itself compiles to
Java 8 bytecode, no JDK 8 needed).

```
gradlew.bat spotlessApply build
```

Artifact: `build/libs/ElinarCurios-<version>.jar` — drop it into the mods folder.

## Controls

| Action | Key |
|---|---|
| Open Infinite Backpack | B (editable in Options → Controls) |

## Usage

1. Take an Infinite Backpack from the creative tab.
2. Right-click to open it (243 huge slots).
3. Click **穿上 / Equip** to wear it on your back.
4. Press **B** anytime to open the worn backpack; right-click the held item also opens it.
5. Click **脱下 / Unequip** in the GUI to take it off.
6. Toggle the Night Vision buff with the small button at the top of the GUI.

**Peace Totem**: craft it with **8 diamond blocks surrounding 1 torch** and
place it on any solid surface — natural hostile mobs stop spawning inside the
ellipsoid around it (radius configurable in `config/ElinarCurios.cfg`).
Mobs from spawners, spawn eggs and commands are unaffected.

## Credits & licenses

- Storage core adapted from **Avaritiaddons** (MPL-2.0, WanionCane).
- Wearing system, Dragon skin/buffs adapted from **AdventureBackpack2** (LGPL-3.0).
- See NOTICE, LICENSE-MPL2.txt, LICENSE-LGPL3.txt.
