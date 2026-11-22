package elinarcurios.mixin;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import elinarcurios.util.BagUtils;
import gregtech.api.hazards.Hazard;
import gregtech.api.hazards.HazardProtection;

/**
 * Everything that asks "is this entity wearing a full hazmat suit?" (heat/frost/electric/radio
 * damage, IC2 radiation and electricity, pollution gas, BartWorks toxic/radioactive items, cells)
 * goes through this single helper. A worn Infinite Backpack counts as a full suit.
 */
@Mixin(value = HazardProtection.class, remap = false)
public abstract class MixinHazardProtection {

    @Inject(method = "isWearingFullHazmatAgainst", at = @At("HEAD"), cancellable = true)
    private static void elinarcurios$backpackIsFullHazmat(final EntityLivingBase entity, final Hazard hazard,
        final CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof EntityPlayer && BagUtils.isWearingInfiniteBackpack((EntityPlayer) entity)) {
            cir.setReturnValue(Boolean.TRUE);
        }
    }
}
