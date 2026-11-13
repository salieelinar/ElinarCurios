package elinarcurios.recipe;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraftforge.oredict.OreDictionary;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.registry.GameRegistry;
import elinarcurios.item.ItemElinarReactorCore;
import elinarcurios.item.ItemElinarTianxuanCore;

/**
 * 天枢·恒温核心的合成表(2026-09-12 初版,用户拍板「先这样定,后期可能改」):
 *
 * <pre>
 * L L L
 * L O L       L = 青金石块(oreDict "blockLapis",GT 已把原版青金石块挂进该词条)
 * L L L       O = 产物:天枢·恒温核心
 * </pre>
 *
 * <p>
 * <b>坑(2026-09-12 实测崩溃,勿重蹈):</b>{@code GameRegistry.addRecipe} <b>不接受</b>
 * oreDict 字符串当材料 —— 它把字符串直接塞进 {@code CraftingManager} 的
 * {@code HashMap<Item, ItemStack>},查不到 key 得到 null,紧接着
 * {@code func_92103_a} 对 null 调 {@code func_77946_l()}(copy)→ 初始化期 NPE,
 * mod 加载失败、游戏卡死在加载界面。**必须先把词条解析成 ItemStack 再注册**;
 * 本仓库其它 mod 的惯例也是 {@code OreDictionary.getOres(name).get(0).copy()}。
 *
 * <p>
 * 解析时若 meta 是 {@code OreDictionary.WILDCARD_VALUE} 要归一成 0(通配 meta
 * 塞进配方会导致匹配永不命中)。
 *
 * <p>
 * 产物 ItemStack <b>不带 NBT</b>:{@link ItemElinarReactorCore#getStoredHeat} 对
 * 无 NBT 的物品一律按 0 处理,所以刚合成的元件天然是"空热"状态,不存在刷热问题。
 */
public final class ReactorCoreRecipes {

    /** 青金石块的 oreDict 词条名。 */
    private static final String LAPIS_BLOCK_ORE = "blockLapis";

    private ReactorCoreRecipes() {}

    public static void register() {
        Item core = ItemElinarReactorCore.instance;
        if (core == null) return;

        ItemStack lapisBlock = resolveLapisBlock();
        if (lapisBlock == null) {
            FMLLog.warning("[ElinarCurios] oreDict '%s' unavailable - reactor core recipe skipped", LAPIS_BLOCK_ORE);
        } else {
            GameRegistry.addRecipe(new ItemStack(core), "LLL", "L L", "LLL", 'L', lapisBlock);
        }

        registerTianxuanRecipe(core);
    }

    // ==================================================================
    // 天玄·恒温核心:9 个天枢·恒温核心 → 1 个(2026-09-21 芷若拍板)
    // ==================================================================

    /**
     * 9 个天枢核心合成 1 个天玄核心。
     *
     * <p>
     * 用<b>无序配方</b>:9 个材料正好占满工作台,「有序」写法对 3×3 全满的摆法反而要靠
     * 匹配顺序去猜,无序更直白也更不容易被别的 mod 的配方冲突。
     *
     * <p>
     * <b>为什么反射构造 {@code ShapeLessRecipe} 而不是 {@code GameRegistry.addShapelessRecipe}:</b>
     * 后者在 1.7.10 Forge 里收 {@code Object...} 并<b>按"对象是否为 ItemStack"来判断</b>原版材料,
     * 传别的类型会被当成"特殊配料"去查 {@code CraftingManager} 的 map —— 而 IC2 的元件
     * (包括天枢核心)是 {@code IReactorComponent},一旦被当成特殊配料就会在初始化期炸。
     * 直接构造 {@code ShapeLessRecipe}(构造器参数就是 {@code Object[]} +
     * {@code CraftingHelper.getIngredient} 等价物)可以避免这整条路径。字段名在不同 Forge 版本
     * 有过变化,所以这里连类名都做了兜底;两条路都失败时只警告,不阻断 mod 加载。
     */
    /**
     * 9 个天枢核心 → 1 个天玄核心的无序配方。
     *
     * <p>
     * <b>必须用原版 {@code ShapelessRecipes} 而不是自定义 {@code IRecipe}</b>(2026-09-21 踩坑):
     * NEI 的合成表是按 recipe handler 种类登记的 —— 它只认 {@code ShapedRecipes} /
     * {@code ShapelessRecipes}({@code codechicken.nei.recipe.ShapelessRecipeHandler} 遍历
     * {@code CraftingManager.getRecipeList()} 时做 {@code instanceof} 判断)。自定义 IRecipe
     * 会正常参与合成,但 NEI 里查不到、点了没反应。
     *
     * <p>
     * 也不走 {@code GameRegistry.addShapelessRecipe}:它在 1.7.10 里按"配料是不是 ItemStack"
     * 分流,IC2 元件(实现了 {@code IReactorComponent})容易被归进特殊分支。直接构造
     * {@code ShapelessRecipes} 只用到稳定的原版 API,配料也全是普通 ItemStack。
     *
     * <p>
     * 材料用<b>通配 meta</b>:天枢核心的 damage 是"储热进度条"(0…100),任何热度都该能参与合成。
     */
    private static void registerTianxuanRecipe(Item core) {
        Item tianxuan = ItemElinarTianxuanCore.instance;
        if (tianxuan == null) {
            FMLLog.warning("[ElinarCurios] tianxuan core item missing - its recipe skipped");
            return;
        }

        List<ItemStack> ingredients = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) {
            ingredients.add(new ItemStack(core, 1, OreDictionary.WILDCARD_VALUE));
        }
        GameRegistry.addRecipe(new ShapelessRecipes(new ItemStack(tianxuan, 1, 0), ingredients));
        // 诊断日志(2026-09-21):NEI 里一直查不到这条配方,先确认它到底有没有被登记。
        // 顺便数一遍 CraftingManager 里"输出是天玄核心"的配方数量,能一次分清是
        // "没注册进去" 还是 "注册进去了但 NEI 不认"。
        if (DEBUG_RECIPE_LOG) {
            int registered = 0;
            for (Object recipe : CraftingManager.getInstance()
                .getRecipeList()) {
                if (recipe instanceof IRecipe && ((IRecipe) recipe).getRecipeOutput() != null
                    && ((IRecipe) recipe).getRecipeOutput()
                        .getItem() == tianxuan) {
                    registered++;
                }
            }
            FMLLog.info(
                "[ElinarCurios] tianxuan recipe registered; crafting-manager entries with this output = %d",
                registered);
        }
    }

    /** 配方诊断日志开关(查清后置 false)。 */
    private static final boolean DEBUG_RECIPE_LOG = true;

    /**
     * 把 {@code blockLapis} 解析成一个具体的 ItemStack。
     *
     * @return 可直接作配方材料的 ItemStack;词条为空时回退原版青金石块,再不行返回 null
     */
    private static ItemStack resolveLapisBlock() {
        List<ItemStack> entries = OreDictionary.getOres(LAPIS_BLOCK_ORE);
        for (ItemStack entry : entries) {
            if (entry == null || entry.getItem() == null) continue;
            ItemStack resolved = entry.copy();
            resolved.stackSize = 1;
            if (resolved.getItemDamage() == OreDictionary.WILDCARD_VALUE) resolved.setItemDamage(0);
            return resolved;
        }
        // 兜底:GT 正常会注册 blockLapis,这里只防词条被别的 mod 意外清空。
        if (Blocks.lapis_block != null) return new ItemStack(Blocks.lapis_block, 1, 0);
        return null;
    }
}
