package elinarcurios.recipe;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.registry.GameRegistry;
import elinarcurios.item.ItemElinarFresnelLens;
import ic2.core.Ic2Items;

/**
 * 菲涅尔透镜的合成表(2026-10-13 新增)。
 *
 * <pre>
 * G G G
 * G R G       G = 玻璃板(原版),R = 中子反射板(IC2)
 * G G G       产物:菲涅尔透镜 ×1
 * </pre>
 *
 * <p>
 * 八块玻璃板围一块 IC2 中子反射板 —— 光学上就是"把一块反射板磨成一圈圈的环带":透镜本体是
 * 玻璃,聚焦中子靠的是中间那块反射板,和它催熟的增殖棒同属核电体系,不需要玩家另外去别的
 * 存矿物链。
 *
 * <p>
 * <b>材料选型说明:</b>原版玻璃板就用 {@code Blocks.glass_pane},不查 oreDict —— GT5U 虽然
 * 注册了 {@code paneGlass} 词条,但登记进词条的是它自己的玻璃板,拿原版方块更稳、也避免
 * "整合包里某个 mod 把词条清空"这类跟本功能无关的失败。中子反射板走 IC2 的公开物品表
 * {@code ic2.core.Ic2Items.reactorReflector}(IC2 是 GTNH 必装,而且是本元件的 API 依赖,
 * 不存在"IC2 缺失"的分支)。
 */
public final class FresnelLensRecipes {

    private FresnelLensRecipes() {}

    public static void register() {
        ItemElinarFresnelLens lens = ItemElinarFresnelLens.instance;
        if (lens == null) {
            FMLLog.warning("[ElinarCurios] fresnel lens item missing - its recipe skipped");
            return;
        }

        ItemStack reflector = Ic2Items.reactorReflector;
        if (reflector == null || reflector.getItem() == null) {
            FMLLog.warning("[ElinarCurios] IC2 neutron reflector unavailable - fresnel lens recipe skipped");
            return;
        }

        GameRegistry.addRecipe(
            new ItemStack(lens),
            "GGG",
            "GRG",
            "GGG",
            'G',
            new ItemStack(Blocks.glass_pane),
            'R',
            reflector.copy());
    }
}
