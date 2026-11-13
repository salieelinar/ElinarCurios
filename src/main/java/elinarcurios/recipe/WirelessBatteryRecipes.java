package elinarcurios.recipe;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.RecipeSorter;

import cpw.mods.fml.common.registry.GameRegistry;
import elinarcurios.item.ItemWirelessBattery;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;

/**
 * 天玄·虚空电池 3×3 工作台合成表(2026-09-12 改版,用户拍板):
 *
 * <pre>
 * A 组 · 基础:ULV = 中心末影珍珠 + 四围红石(无电量门槛)
 * B 组 · GT 锂电路线:LV/MV/HV = 中心末影珍珠 + 四围 GT 小/中/大型锂电池(无电量门槛)
 * C 组 · 线 1 · 虚空升级路线:EV~MAX = 中心末影之眼块(GT 材料块)+ 四围上一级虚空电池(四角必须全部满电)
 * D 组 · 线 2 · 同级直通路线(2026-09-13 用户拍板):
 *      ULV = 中心末影珍珠 + 四围 4 个钽电容;
 *      EV~MAX = 中心末影之眼块 + 四围 4 个同级 GT 电池(空/满皆可,无电量门槛)
 * </pre>
 *
 * <p>
 * 两条铁律(解决"无中生电太廉价"):
 * <ol>
 * <li><b>产物一律是空电池</b> —— 由 {@link ItemWirelessBattery#newEmptyBattery(int)}
 * 写入 EC.Charge = 0。旧版产物无 NBT,而 getRealCharge 把无 NBT 当成满电,
 * 于是"4 个空电池 → 1 个满电池"就能白刷 EU。顺带:产物带 NBT 后,
 * NEI 显示的产物与实际合成结果一致(都显示 0%)。</li>
 * <li><b>线 1 的 EV~MAX 要求四角上一级电池满电</b> —— 原版 ShapedRecipes 只比对
 * item + damage、完全不看 NBT,所以必须走 {@link CrossBatteryRecipe}
 * 自定义匹配。ULV~HV 与线 2 的全部档位都不设电量门槛(线 2 吃的是同级
 * GT 电池,合成出来本来就是空的)。</li>
 * </ol>
 *
 * <p>
 * 能量账:容量 = V×15625 每档 ×4,而 C 组正好吃 4 个上一级电池 ——
 * "4 块满电上一级 → 1 块空下一级"即每次升级消化掉一块上一级满电电池
 * (≈ 新档容量的 25%)。MAX 档 V[14] = Integer.MAX_VALUE-7 不是精确 4 倍,
 * 但因产物为空,不存在溢出/损耗问题。线 2(D 组)是"4 块同级 GT 电池 → 1 块
 * 同级空虚空电池",卖的是升级捷径(不必逐级攒虚空电池),不是能量换算。
 *
 * <p>
 * 依赖 GregTech(锂电 meta、材料块)与 IC2,注册点调用方(CommonProxy)有
 * isModLoaded 守卫,无 GT/IC2 时本类不会被加载。
 */
public final class WirelessBatteryRecipes {

    private WirelessBatteryRecipes() {}

    public static void register() {
        ItemWirelessBattery battery = ItemWirelessBattery.instance;
        if (battery == null) return;

        // 自定义 IRecipe 子类必须向 Forge 的 RecipeSorter 登记类别:排序表是按
        // "类名"查表的,没登记的类会被打上 "Unknown recipe class! ... Modder please
        // refer to net.minecraftforge.oredict.RecipeSorter" 并归入 UNKNOWN 类别 ——
        // 排序位置不确定,与其它同形状配方之间有抢先匹配的隐患。
        // 本类继承 ShapedRecipes(3×3 定形图案),所以登记为 SHAPED。
        RecipeSorter.register(
            "elinarcurios:cross_battery",
            CrossBatteryRecipe.class,
            RecipeSorter.Category.SHAPED,
            "after:minecraft:shaped");

        Item pearl = Items.ender_pearl;

        // ---- A 组:ULV(tier 0)----
        addCross(0, new ItemStack(Items.redstone), new ItemStack(pearl), false);

        // ---- B 组:LV / MV / HV(tier 1..3),GT 原版锂电池 ----
        // 注意:必须走 GT 官方 ItemList(跨版本稳定),不能硬编码 meta id ——
        // gt.metaitem.01 的 damage 表在不同 GT 版本间会变(曾把 518 解析成褐煤粉)。
        // GT 锂电池合成出来就是空的,所以这三档不设满电门槛。
        ItemStack lvBattery = ItemList.Battery_RE_LV_Lithium.get(1);
        ItemStack mvBattery = ItemList.Battery_RE_MV_Lithium.get(1);
        ItemStack hvBattery = ItemList.Battery_RE_HV_Lithium.get(1);
        if (lvBattery == null || mvBattery == null || hvBattery == null) return;
        addCross(1, lvBattery, new ItemStack(pearl), false);
        addCross(2, mvBattery, new ItemStack(pearl), false);
        addCross(3, hvBattery, new ItemStack(pearl), false);

        // ---- D 组(线 2)的 ULV 支线:中心末影珍珠 + 四围 4 个钽电容 ----
        // 与 A 组(红石)并行:同中心、只换四周材料,给 ULV 两条线。
        ItemStack tantalum = ItemList.Battery_RE_ULV_Tantalum.get(1);
        if (tantalum != null) {
            addCross(0, tantalum, new ItemStack(pearl), false);
        }

        // ---- C 组(线 1):EV ~ MAX(tier 4..14),GT 末影之眼材料块 + 四角满电门槛 ----
        ItemStack eyeBlock = Materials.EnderEye.getBlocks(1);
        if (eyeBlock == null) return; // 材料块未注册(与本快照 GT 版本相关)
        for (int tier = 4; tier < ItemWirelessBattery.TIER_COUNT; tier++) {
            addCross(tier, new ItemStack(battery, 1, tier - 1), eyeBlock, true);
        }

        // ---- D 组(线 2):EV ~ MAX = 中心末影之眼块 + 四围 4 个同级 GT 电池 ----
        // 与 C 组并行:线 1 吃"上一级虚空电池(必须满电)",线 2 吃"同级 GT 电池
        // (空/满皆可)" —— 绕开逐级升级链,直接用 4 块同级 GT 电池换一块同级
        // 虚空电池。产物仍是空电池,所以不存在无中生电。
        // 材料与档位一一对应(索引 0 = tier 4/EV);单个档位取不到时跳过该档,
        // 不影响其余档位的注册。
        ItemList[] sameTierCells = { ItemList.BatteryHull_EV_Full, // EV 小型阳光化合物电池
            ItemList.BatteryHull_IV_Full, // IV 中型阳光化合物电池
            ItemList.BatteryHull_LuV_Full, // LuV 大型阳光化合物电池
            ItemList.BatteryHull_ZPM_Full, // ZPM 中型超能硅岩电池
            ItemList.BatteryHull_UV_Full, // UV 大型超能硅岩电池
            ItemList.BatteryHull_UHV_Full, // UHV 小型中子电池
            ItemList.BatteryHull_UEV_Full, // UEV 中型中子电池
            ItemList.BatteryHull_UIV_Full, // UIV 大型中子电池
            ItemList.BatteryHull_UMV_Full, // UMV 中型无尽锭电池
            ItemList.BatteryHull_UxV_Full, // UXV 大型无尽锭电池
            ItemList.ZPM5 }; // MAX 狂·终极电池
        for (int i = 0; i < sameTierCells.length; i++) {
            ItemStack cell = sameTierCells[i].get(1);
            if (cell == null) continue; // 单档缺失不影响其余档位
            addCross(4 + i, cell, eyeBlock, false);
        }
    }

    /**
     * 十字形 3×3 配方:中心 center,上下左右 side(四角留空);产物是该档的空电池。
     *
     * <pre>
     *  S
     * SCS
     *  S
     * </pre>
     *
     * @param requireFull C 组传 true —— 四角上一级电池必须满电才匹配
     */
    private static void addCross(int tier, ItemStack side, ItemStack center, boolean requireFull) {
        GameRegistry
            .addRecipe(new CrossBatteryRecipe(ItemWirelessBattery.newEmptyBattery(tier), side, center, requireFull));
    }
}
