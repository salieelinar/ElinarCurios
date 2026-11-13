# Elinar Curios

GTNH（Minecraft 1.7.10）模组，核心是**无限背包**：243 格，每格可堆 21 亿个同类物品，能背在身上用快捷键打开。

原名 Infinithpackbag，v1.0.0 起更名 Elinar Curios，modId 仍为 `elinarcurios`。

## 无限背包

- 243 格，单格上限 2,147,483,647（Integer.MAX_VALUE），自动合并、溢出安全
- 500×276 无限箱风格界面，数量按 K / M / B 显示
- 可穿在背上（自带龙形背包皮肤），穿戴时提供夜视，可在界面里开关
- 快捷键 `B` 打开背包，可在「控制」里改键
- 死亡掉落；限制维度（dim 55）有保护

## 和平图腾

- 放在任意实心方块上，阻止周围椭球范围内自然刷怪（默认水平 64 / 垂直 32）
- 刷怪笼、刷怪蛋、`/summon` 不受影响

## GTNH 相关方块

| 方块 | 作用 |
|---|---|
| 天玄虚空能量节点 | 缓存线缆 EU 并注入无线电网 |
| 样板总成镜像（物品 / 流体） | 贴在装配线输入总成位置，用数据棒绑定样板总成，零管道直供 |
| 高级装配线 | 原版装配线结构的 1:1 克隆，支持多安 / 激光能源仓与 GTNL 并行控制仓 |
| 菲涅尔透镜 | 让反应堆内每个增殖单元均匀受中子辐照 |
| ME 输出快速刷出 | 机器工作时若有玩家下单的合成任务，把输出总成改为每 tick 推送 |

## 安装

把 `ElinarCurios-<版本>.jar` 放进整合包的 `mods` 目录。

## 构建

需要 JDK 25 运行 Gradle（产物仍是 Java 8 字节码）：

```
gradlew.bat spotlessApply build
```

产物在 `build/libs/ElinarCurios-<版本>.jar`。

## 配置

`config/ElinarCurios.cfg`。

## 许可

- 无限箱存储核心改编自 Avaritiaddons（MPL-2.0）
- 穿戴系统与皮肤改编自 AdventureBackpack2（LGPL-3.0）
- 详见 `NOTICE` 与 `LICENSE-*.txt`

实现细节见 `DEV_GUIDE.md`，菲涅尔透镜的交付说明见 `FRESNEL_LENS.md`。
