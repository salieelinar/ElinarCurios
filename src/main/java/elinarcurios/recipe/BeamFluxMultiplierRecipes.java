package elinarcurios.recipe;

import net.minecraft.item.ItemStack;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.registry.GameRegistry;
import elinarcurios.machine.ElinarMachines;
import gregtech.api.enums.ItemList;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gtnhlanth.common.register.LanthItemList;

/**
 * 束流倍流器的合成表（默认方案，2026-11-07；2026-11-20 边角件换成超稳定黑洞种子）。
 *
 * <pre>
 * A B A
 * B C B       A = 束流管（BEAMLINE_PIPE）
 * A B A       B = 超稳定黑洞种子（GT: Black_Hole_Stabilizer）
 *             C = ZPM 发射器
 *             产物：束流倍流器 ×1
 * </pre>
 *
 * <p>
 * 束流线本身是 ZPM 阶段内容，核心件仍是 ZPM 发射器；四个边角件由 ZPM 机壳换成 GT 的超稳定黑洞种子
 * （黑洞压缩机 BHC 的稳定催化剂，见 GT5U {@code MTEBlackHoleCompressor#searchAndDecrementCatalysts}）。
 * 束流管物品来自 GT5U 的 {@code LanthItemList}。任一物品为 null（整合包把束流线关了 / GT 版本没有该物品）
 * 时只跳过本配方，不影响方块注册。
 */
public final class BeamFluxMultiplierRecipes {

    private BeamFluxMultiplierRecipes() {}

    public static void register() {
        final IMetaTileEntity machine = ElinarMachines.getBeamFluxMultiplier();
        if (machine == null) {
            FMLLog.warning("[ElinarCurios] beam flux multiplier missing - its recipe skipped");
            return;
        }

        final ItemStack beamPipe = LanthItemList.BEAMLINE_PIPE;
        final ItemStack seed = ItemList.Black_Hole_Stabilizer.get(1);
        final ItemStack emitter = ItemList.Emitter_ZPM.get(1);
        if (beamPipe == null || seed == null || emitter == null) {
            FMLLog.warning("[ElinarCurios] beamline parts unavailable - beam flux multiplier recipe skipped");
            return;
        }

        GameRegistry.addRecipe(machine.getStackForm(1), "ABA", "BCB", "ABA", 'A', beamPipe, 'B', seed, 'C', emitter);
    }
}
