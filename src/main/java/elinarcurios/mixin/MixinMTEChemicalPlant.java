package elinarcurios.mixin;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import elinarcurios.Config;
import gtPlusPlus.xmod.gregtech.common.tileentities.machines.multi.production.chemplant.MTEChemicalPlant;

/**
 * Releases the hatch tier cap of the ExxonMobil Chemical Plant (GT++).
 *
 * <p>
 * {@code MTEChemicalPlant.checkMachine()} ends with
 * {@code (mMachineCasingTier >= 9 || mMachineCasingTier >= maxTierOfHatch) && ...} where
 * {@code maxTierOfHatch} is the highest tier among the machine's input/output buses and hatches
 * (energy hatches included) and {@code mMachineCasingTier} is the tier of the ten internal machine
 * casings. That means a plant built with HV casings refuses an UEV Crafting Input Buffer (ME), which
 * is the only way to feed patterns into it before UHV casings are affordable.
 *
 * <p>
 * Redirecting the single read of {@code maxTierOfHatch} inside {@code checkMachine} to
 * {@code Integer.MIN_VALUE} makes {@code mMachineCasingTier >= maxTierOfHatch} always true, so only
 * the untouched {@code mCatalystBuses.size() <= 1} condition remains. The recipe tier (governed by
 * {@code mSolidCasingTier}, i.e. the outer solid casings) is not affected, and structural checks
 * still require ten same-tier internal casings. Only this machine is touched.
 */
@Mixin(value = MTEChemicalPlant.class, remap = false)
public abstract class MixinMTEChemicalPlant {

    @Shadow
    private int maxTierOfHatch;

    @Redirect(
        method = "checkMachine",
        at = @At(
            value = "FIELD",
            opcode = Opcodes.GETFIELD,
            target = "LgtPlusPlus/xmod/gregtech/common/tileentities/machines/multi/production/chemplant/MTEChemicalPlant;maxTierOfHatch:I"))
    private int elinarcurios$relaxHatchTierCap(MTEChemicalPlant machine) {
        return Config.chemPlantRelaxHatchTier ? Integer.MIN_VALUE : this.maxTierOfHatch;
    }
}
