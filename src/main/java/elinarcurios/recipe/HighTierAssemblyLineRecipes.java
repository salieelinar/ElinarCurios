package elinarcurios.recipe;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.registry.GameRegistry;
import elinarcurios.machine.ElinarMachines;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;

/**
 * 高级装配线的原型配方（从独立验证版 ElinarAssemblyLine 0.1.1 原样并入，2026-10-18）。
 *
 * <pre>
 * A B A
 * C D C       A = 钻石, B = 末影珍珠, C = 钻石块, D = 下界之星
 * A B A       产物:高级装配线 ×1
 * </pre>
 *
 * <p>
 * 这是独立验证阶段用来保证"NEI 能查到、生存能拿到"的临时配方，刻意保持简单；
 * 正式平衡配方等芷若给表后再替换（机器本体不依赖配方，拆掉本类也不会影响已放置的方块）。
 */
public final class HighTierAssemblyLineRecipes {

    private HighTierAssemblyLineRecipes() {}

    public static void register() {
        final IMetaTileEntity mte = ElinarMachines.getHighTierAssemblyLine();
        if (mte == null) {
            FMLLog.warning("[ElinarCurios] high-tier assembly line missing - its recipe skipped");
            return;
        }

        GameRegistry.addRecipe(
            mte.getStackForm(1),
            "ABA",
            "CDC",
            "ABA",
            'A',
            Items.diamond,
            'B',
            Items.ender_pearl,
            'C',
            Blocks.diamond_block,
            'D',
            Items.nether_star);
    }
}
