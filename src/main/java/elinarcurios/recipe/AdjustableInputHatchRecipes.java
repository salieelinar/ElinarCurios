package elinarcurios.recipe;

import net.minecraft.item.ItemStack;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.registry.GameRegistry;
import elinarcurios.machine.ElinarMachines;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.MetaTileEntityIDs;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;

/**
 * 可调式流体输入仓的合成表（沿用「高级放射仓」的 9 换 1 惯例）。
 *
 * <pre>
 * I I I
 * I I I       I = 原版流体输入仓（任意电压：ULV ~ UHV 共 10 种各注册一条）
 * I I I       产物：可调式流体输入仓 ×1
 * </pre>
 *
 * <p>
 * 原版输入仓是同一个 MTE 类的不同 tier，贴图颜色不同、容量不同；本方块不看电压等级，
 * 拿着哪一档的输入仓都能直接合成，不用为了凑配方再去凑 9 个指定等级的。
 */
public final class AdjustableInputHatchRecipes {

    private AdjustableInputHatchRecipes() {}

    public static void register() {
        final IMetaTileEntity adjustable = ElinarMachines.getInputHatchAdjustable();
        if (adjustable == null) {
            FMLLog.warning("[ElinarCurios] adjustable input hatch missing - its recipe skipped");
            return;
        }

        final ItemStack output = adjustable.getStackForm(1);
        int registered = 0;
        for (MetaTileEntityIDs id : MetaTileEntityIDs.values()) {
            if (!id.name()
                .startsWith("INPUT_HATCH_")) continue;
            final ItemStack original = new ItemStack(GregTechAPI.sBlockMachines, 1, id.ID);
            GameRegistry.addRecipe(output, "###", "###", "###", '#', original);
            registered++;
        }
        if (registered == 0) {
            FMLLog.warning("[ElinarCurios] no input hatch tiers found - adjustable input hatch recipe skipped");
        }
    }
}
