package elinarcurios.recipe;

import net.minecraft.item.ItemStack;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.registry.GameRegistry;
import elinarcurios.machine.ElinarMachines;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.MetaTileEntityIDs;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;

/**
 * 高级放射仓的合成表（芷若 2026-10-31 拍板：「9个原版合成1个高级放射仓」）。
 *
 * <pre>
 * R R R
 * R R R       R = 原版放射仓（任意电压等级，HV~MAX 共 12 种各注册一条）
 * R R R       产物:高级放射仓 ×1
 * </pre>
 *
 * <p>
 * 原版放射仓本身有 HV~MAX 共 12 个等级（同一 MTE 类的不同 tier，贴图颜色不同）。她说的
 * 「原版放射仓」就是其中任意一个，所以这里按 {@link MetaTileEntityIDs} 里所有
 * {@code RadioHatch_} 条目各注册一条 3×3 配方 —— 手里是哪个等级的放射仓都能直接拿来合，
 * 不用为了凑配方再去造 9 个指定等级的。
 */
public final class AdvancedRadioHatchRecipes {

    private AdvancedRadioHatchRecipes() {}

    public static void register() {
        final IMetaTileEntity advanced = ElinarMachines.getAdvancedRadioHatch();
        if (advanced == null) {
            FMLLog.warning("[ElinarCurios] advanced radio hatch missing - its recipe skipped");
            return;
        }

        final ItemStack output = advanced.getStackForm(1);
        int registered = 0;
        for (MetaTileEntityIDs id : MetaTileEntityIDs.values()) {
            if (!id.name()
                .startsWith("RadioHatch_")) continue;
            final ItemStack original = new ItemStack(GregTechAPI.sBlockMachines, 1, id.ID);
            GameRegistry.addRecipe(output, "###", "###", "###", '#', original);
            registered++;
        }
        if (registered == 0) {
            FMLLog.warning("[ElinarCurios] no radio hatch tiers found - advanced radio hatch recipe skipped");
        }
    }
}
