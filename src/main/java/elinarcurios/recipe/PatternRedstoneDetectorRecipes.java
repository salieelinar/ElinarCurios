package elinarcurios.recipe;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.registry.GameRegistry;
import elinarcurios.machine.ElinarMachines;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;

/**
 * 样板总成侦测器的合成表（芷若 2026-11-07 拍板：「合成表是9个红石火把」）。
 *
 * <pre>
 * # # #
 * # # #       # = 红石火把
 * # # #       产物：样板总成侦测器 ×1
 * </pre>
 */
public final class PatternRedstoneDetectorRecipes {

    private PatternRedstoneDetectorRecipes() {}

    public static void register() {
        final IMetaTileEntity detector = ElinarMachines.getPatternRedstoneDetector();
        if (detector == null) {
            FMLLog.warning("[ElinarCurios] pattern redstone detector missing - its recipe skipped");
            return;
        }

        GameRegistry
            .addRecipe(detector.getStackForm(1), "###", "###", "###", '#', new ItemStack(Blocks.redstone_torch));
    }
}
