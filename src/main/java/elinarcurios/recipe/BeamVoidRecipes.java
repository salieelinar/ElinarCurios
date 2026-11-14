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
 * 束流虚空输入仓 = 原版「过滤型束流出束仓」 + 4×ZPM 机壳 + 4×束流管
 * 束流虚空输出仓 = 原版「束流输入仓」       + 4×ZPM 机壳 + 4×束流管
 * 束流虚空存储器 = ZPM 发射器               + 4×ZPM 机壳 + 4×束流管
 * 束流虚空输入室 = 束流虚空输入仓           + 4×ZPM 机壳 + 4×束流管
 *
 *   A B A
 *   B C B
 *   A B A      A=束流管  B=ZPM 机壳  C=核心件
 * </pre>
 *
 * <p>
 * 束流线相关的物品来自 GT5U 的 {@code LanthItemList} / {@code ItemList}；如果整合包把束流线关了
 * （物品为 null），只跳过对应配方，不影响方块注册。
 */
public final class BeamVoidRecipes {

    private BeamVoidRecipes() {}

    public static void register() {
        final ItemStack beamPipe = LanthItemList.BEAMLINE_PIPE;
        final ItemStack hull = ItemList.Hull_ZPM.get(1);
        if (beamPipe == null || hull == null) {
            FMLLog.warning("[ElinarCurios] beamline pipe / ZPM hull unavailable - beam void recipes skipped");
            return;
        }

        registerOne(
            ElinarMachines.getBeamVoidInputHatch(),
            "beam void input hatch",
            ItemList.AdvancedBeamlineOutputHatch.get(1),
            beamPipe,
            hull);
        registerOne(
            ElinarMachines.getBeamVoidOutputHatch(),
            "beam void output hatch",
            LanthItemList.LUV_BEAMLINE_INPUT_HATCH,
            beamPipe,
            hull);
        registerOne(
            ElinarMachines.getBeamVoidStorage(),
            "beam void storage",
            ItemList.Emitter_ZPM.get(1),
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
        ItemStack hull) {
        if (machine == null || core == null) {
            FMLLog.warning("[ElinarCurios] " + what + " recipe skipped (missing item)");
            return;
        }
        GameRegistry.addRecipe(machine.getStackForm(1), "ABA", "BCB", "ABA", 'A', beamPipe, 'B', hull, 'C', core);
    }
}
