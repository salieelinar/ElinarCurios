package elinarcurios.recipe;

import net.minecraft.item.ItemStack;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.registry.GameRegistry;
import elinarcurios.machine.ElinarMachines;
import gregtech.api.enums.ItemList;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gtnhlanth.common.register.LanthItemList;

/**
 * 束流虚空四件套的合成表（2026-11-14 从独立验证版并入）。
 *
 * <pre>
 * 束流虚空输入仓 = 原版「过滤型束流出束仓」 + 4×黑洞种子 + 4×束流管
 * 束流虚空输出仓 = 原版「束流输入仓」       + 4×黑洞种子 + 4×束流管
 * 束流虚空存储器 = 超稳定黑洞种子           + 4×ZPM 机壳 + 4×束流管
 * 束流虚空输入室 = 束流虚空输入仓           + 4×ZPM 机壳 + 4×束流管
 *
 *   A B A
 *   B C B      A=束流管  B=边角件（见上表）  C=核心件
 *   A B A
 * </pre>
 *
 * <p>
 * 物品全部来自 GT5U 的 {@code LanthItemList} / {@code ItemList}：
 * <ul>
 * <li>黑洞种子 = {@code ItemList.Black_Hole_Opener}（亚稳态黑洞的催化剂）—— 输入仓 / 输出仓的 4 个边角件
 * （2026-11-20 芷若定案，原为 4×ZPM 机壳）；</li>
 * <li>超稳定黑洞种子 = {@code ItemList.Black_Hole_Stabilizer} —— 存储器的核心件。</li>
 * </ul>
 * 两者都是黑洞压缩机（BHC）的催化剂，见 GT5U {@code MTEBlackHoleCompressor#searchAndDecrementCatalysts}；
 * GT 只注册物品、配方由 GTNH CoreMod 给。任一物品为 null（整合包没开束流线 / GT 版本没有该物品）时只跳过
 * 对应配方并打日志，不影响方块注册。
 */
public final class BeamVoidRecipes {

    private BeamVoidRecipes() {}

    public static void register() {
        final ItemStack beamPipe = LanthItemList.BEAMLINE_PIPE;
        if (beamPipe == null) {
            FMLLog.warning("[ElinarCurios] beamline pipe unavailable - beam void recipes skipped");
            return;
        }
        final ItemStack hull = ItemList.Hull_ZPM.get(1);
        // 普通黑洞种子（亚稳态）：输入仓 / 输出仓的边角件。
        final ItemStack blackHoleSeed = ItemList.Black_Hole_Opener.get(1);

        registerOne(
            ElinarMachines.getBeamVoidInputHatch(),
            "beam void input hatch",
            ItemList.AdvancedBeamlineOutputHatch.get(1),
            beamPipe,
            blackHoleSeed);
        registerOne(
            ElinarMachines.getBeamVoidOutputHatch(),
            "beam void output hatch",
            LanthItemList.LUV_BEAMLINE_INPUT_HATCH,
            beamPipe,
            blackHoleSeed);
        registerOne(
            ElinarMachines.getBeamVoidStorage(),
            "beam void storage",
            // 核心件换成了 GT 的超稳定黑洞种子（2026-11-20 芷若定案）：原来这里是 ZPM 发射器。
            // 该物品是黑洞压缩机（BHC）里把黑洞锁成"超级稳定"的催化剂，见 GT5U
            // MTEBlackHoleCompressor#searchAndDecrementCatalysts；GT 只注册物品，配方由 GTNH CoreMod 给。
            ItemList.Black_Hole_Stabilizer.get(1),
            beamPipe,
            hull);
        registerOne(
            ElinarMachines.getBeamVoidInputChamber(),
            "beam void input chamber",
            ElinarMachines.getBeamVoidInputHatch() == null ? null
                : ElinarMachines.getBeamVoidInputHatch()
                    .getStackForm(1),
            beamPipe,
            hull);
    }

    private static void registerOne(IMetaTileEntity machine, String what, ItemStack core, ItemStack beamPipe,
        ItemStack edge) {
        if (machine == null || core == null || beamPipe == null || edge == null) {
            FMLLog.warning("[ElinarCurios] " + what + " recipe skipped (missing item)");
            return;
        }
        GameRegistry.addRecipe(machine.getStackForm(1), "ABA", "BCB", "ABA", 'A', beamPipe, 'B', edge, 'C', core);
    }
}
