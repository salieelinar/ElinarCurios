# 菲涅尔透镜（Fresnel Lens）· 交付说明

> 2026-10-13 新增。核反应堆元件：把 GT5U 增殖棒的辐照从"贴着四格"变成"全堆每一格、完全均匀"。

---

## 一、它解决的问题（机制取证）

她截图里的东西是 **GT 的增殖棒**：

| 项 | 值 | 出处（2026-10-13 取证） |
|---|---|---|
| 内部类 | `gregtech.api.items.ItemBreederCell` | `ItemBreederCell.java:31` |
| 荧石款 | `ItemList.RodGlowstone` → `Fuel Rod (Glowstone)` | `LoaderGTBlockFluid.java:254` |
| 产物 | `DepletedRodGlowstone` → `Fuel Rod (Sunnarium)`（阳光化合物棒） | `LoaderGTBlockFluid.java:253` |
| 热量步长 | **3000**（`mHeatBonusStep`） | 同上，`javap` 在 `gregtech-5.09.54.183.jar` 里读到 `sipush 3000` |
| 倍率 | 1 | 同上 |
| 所需脉冲 | **10000**（= `setMaxDamage`） | 同上，`sipush 10000` |

**进度公式**（`ItemBreederCell.getNewDamage`，与游戏里跑的 jar 字节码逐条对齐）：

```
每次收到中子脉冲：进度 += 1 + 堆芯热量 / 3000
进度 >= 10000    ：该格原地替换成产物
```

它自己 `processChamber` 是**空的** —— 不产热、不发电、不吸热，纯被动挨打。

**"辐射"就是 IC2 的中子脉冲**。原版燃料棒 `ItemReactorUranium.processChamber` 只往
**自己上下左右四格**各调一次 `acceptUraniumPulse`，所以传统玩法必须贴身摆位，且各格进度
被邻居布局拖得不齐。

**IC2 反应堆的调用节奏**（反编译 `TileEntityNuclearReactorElectric` 确认）：
每秒（`getTickRate() == 20`）结算一次，每次结算把全网格扫**两趟**：

| 趟 | `heatrun` | 增殖棒行为 | 燃料棒行为 |
|---|---|---|---|
| pass 0 | **true**（热趟） | **进度在这里累加** | 产热（`triangularNumber(pulses)`） |
| pass 1 | false（能量趟） | 只返回 `deflector`（默认 false） | `addOutput` 发电 |

---

## 二、透镜的做法

在**热趟**遍历全堆 9×6 网格，对每一格"合格的增殖元件"各发 **4 次**中子脉冲。

```
每格每秒进度 = 4 × (1 + 堆芯热量 / 3000)
```

**为什么是热趟而不是能量趟**：增殖棒只在热趟累加进度；而热趟发脉冲**不产生任何 EU**
（燃料棒的 `addOutput` 写在 `if (!heatrun)` 里）。这与天玄·恒温核心故意走能量趟刷产能
是相反的两条路。

**为什么均匀**：增殖棒的进度只读 `reactor.getHeat()` 这个**堆芯全局标量**，不存在"按格
分摊的辐射值"。透镜自己不产热、不吸热（`canStoreHeat=false`、`alterHeat` 原样返回），
所以只要堆芯热量不变，全堆每一格逐 tick 完全同步 —— 从插进去那一刻一起涨，到 10000 一起变产物。

**为什么跳过燃料棒 / 反射板 / 冷却单元**：判定条件是**结构性的、不认类名** ——
`canStoreHeat == false` **且** `getMaxHeat == 0`。三类元件因此全部被排除：

- 燃料棒被打到会返回 true → 会被算进相邻燃料棒的 `pulses` → 产热是 `triangularNumber`
  的**平方级**上涨，可能把堆顶爆；
- IC2 反射板被打到会**扣损坏度**（用完消失）；
- GT5U 反射板 `ItemNeutronReflector` 会把脉冲**转发回燃料棒**；
- 散热片 / 冷却单元在热趟会真的排热。

同一判据也天然覆盖其他 mod 的增殖棒（IC2 锂增殖棒、NuclearHorizons 的增殖棒等）。

---

## 三、速度表（4 脉冲/tick）

| 堆芯热量 | 每格进度/tick | 单格满 10000 用时 | 54 格全转化（同步完成） |
|---|---|---|---|
| 0（纯增殖堆） | 4 | 2500 秒 ≈ **41.7 min** | ≈ 41.7 min |
| 3000 | 8 | 1250 秒 ≈ 20.8 min | ≈ 20.8 min |
| 6000 | 12 | ≈ 13.9 min | ≈ 13.9 min |
| 9900（配天玄·恒温核心的 99% 锁温） | 17 | ≈ 9.8 min | ≈ 9.8 min |

**转化速度仍然由你堆的堆芯热量决定**（透镜不改数值平衡），它只负责"覆盖全堆 + 完全均匀"。
放 N 枚透镜 = 每格每 tick `4N` 次脉冲，线性叠加。

---

## 四、配方

```
玻璃板 玻璃板 玻璃板
玻璃板 反射板 玻璃板      → 菲涅尔透镜 ×1
玻璃板 玻璃板 玻璃板
```

- 玻璃板 = 原版 `Blocks.glass_pane`（不走 oreDict，避开"某 mod 清空词条"这类无关失败）
- 反射板 = IC2 中子反射板 `ic2.core.Ic2Items.reactorReflector`
- 想换材料：改 `recipe/FresnelLensRecipes.java` 一处即可；想改回"任务书/奖励发放"
  就删掉 `CommonProxy.init` 里那一行 `FresnelLensRecipes.register();`

---

## 五、进游戏可验证的现象

1. **创造页 / NEI**：搜 `菲涅尔透镜` 或 `Fresnel Lens`，图标是青色同心环 + 金边的镜头。
   tooltip 应有：`辐照强度 4 次中子脉冲/秒/格` / `辐照范围 整堆每一格` /
   `自身开销 不产热、不耗电、不磨损` + 三行灰字说明 + 烫金一句「万束中子，同赴一焦」。
2. **配方**：NEI 里查菲涅尔透镜，应有 8 玻璃板 + 1 中子反射板 的合成表。
3. **反应堆实测**（关键）：
   - 把透镜 + 若干**荧石棒**（NEI 搜 `Fuel Rod (Glowstone)` / 阳光化合物）插进核电 GUI；
   - 关掉红石（堆不发电）也照样工作；
   - 所有荧石棒的 `耐久 1/1` 一类进度**同步上升**，且**与透镜放在哪一格无关**；
   - 满 10000 时**同一秒集体**变成「阳光化合物棒」。
4. **反例校验**（说明它不乱来）：堆里放燃料棒时，产热/发电量应与不放透镜时**完全一致**
   （透镜对燃料棒零影响）；中子反射板不会掉耐久。

---

## 六、配置

`config/ElinarCurios.cfg`：

```ini
general {
    I:fresnelLensPulsesPerTick=4
}
```

每格增殖元件每个反应堆 tick（=1 秒）吃到的脉冲次数。`0` = 关闭辐照（只留物品本体）。
改完重进游戏生效。

---

## 七、验证状态（**务必先读**）

| 项 | 状态 |
|---|---|
| 编译 + reobf + 打包 | ✅ 通过（`spotlessApply build`，27 tasks，BUILD SUCCESSFUL） |
| jar 内容核验 | ✅ `ItemElinarFresnelLens.class` / `FresnelLensRecipes.class` / `fresnelLens.png` / 双语 lang 均在包内 |
| **离线行为探针** | ✅ **17 项断言全过**（`D:\Agent\Work\lensprobe\LensProbe.java`）—— 用**真实编译产物 + 真实 IC2 API**驱动 `processChamber`，验证：热趟每格 4 次 / 能量趟零次 / 不产 EU / 不动堆芯热量与 maxHeat 与 HEM / 储热类元件被跳过 / 空堆不崩 / 多枚线性叠加 / 透镜本身不被照射 |
| dev `runClient` 冒烟 | ❌ **起不来，但是既有的 dev 环境缺口，与本次改动无关**：dev classpath 缺 IC2（`run/client/mods` 为空目录），`ItemWirelessBattery` 撞在 `ic2.api.item.ISpecialElectricItem` 上先崩；`ItemElinarFresnelLens` 连加载机会都没有 |
| **实机（她）** | ✅ **2026-10-13 实机测试通过**（"我刚测试过了那个菲涅尔透镜元件没问题"）—— 注册、贴图、配方、tooltip 全部确认 |

> 因此：**功能正确性有离线探针背书，游戏内集成已由她实机确认，本元件随 v1.2.9 发版。**

---

## 八、交付产物（已随 v1.2.9 发版）

- **`dist\ElinarCurios-v1.2.9.jar`**（236,201 bytes，MD5 `6A2D4150569E480CE7805CEDFB261192`），tag `v1.2.9` = commit `11ec51a`，jar 内自报版本 `v1.2.9`（干净，无 `-dirty`）。
- 本版 = v1.2.8 基线 + 菲涅尔透镜 + 无线电池 `RecipeSorter` 补登记 + unimixins dev 运行期依赖 + 化工厂 mixin 修正；**水世界整条线已作废清除**。
- 构建产物在 `build\libs\`（`ElinarCurios-v1.2.9.jar` / `-dev.jar` / `-sources.jar`），交付副本只放 `dist\`。

## 九、改到哪了（文件清单）

| 文件 | 改动 |
|---|---|
| `src/main/java/elinarcurios/item/ItemElinarFresnelLens.java` | **新增**：元件本体（IReactorComponent 实现） |
| `src/main/java/elinarcurios/recipe/FresnelLensRecipes.java` | **新增**：配方 |
| `src/main/resources/assets/elinarcurios/textures/items/fresnelLens.png` | **新增**：16×16 图标（程序生成） |
| `src/main/java/elinarcurios/CommonProxy.java` | 注册物品（IC2 守卫内）+ 配方注册 + `Config.ensureLoaded()` |
| `src/main/java/elinarcurios/Config.java` | 新增 `fresnelLensPulsesPerTick`（默认 4）+ `ensureLoaded()` |
| `src/main/resources/assets/elinarcurios/lang/zh_CN.lang` / `en_US.lang` | 名称 + tooltip 双语 |
