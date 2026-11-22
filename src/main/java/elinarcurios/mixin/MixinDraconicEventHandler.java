package elinarcurios.mixin;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import elinarcurios.util.BagUtils;

/**
 * Draconic Evolution's jump boost and mining bonus live in its generic event handler (not in the
 * armour handler), so they have to be suppressed separately: with both worn the two effects would
 * stack (double jump, 5x/25x mining twice).
 */
@Mixin(targets = "com.brandon3055.draconicevolution.common.handler.MinecraftForgeEventHandler", remap = false)
public abstract class MixinDraconicEventHandler {

    @Inject(method = "onLivingJumpEvent", at = @At("HEAD"), cancellable = true)
    private void elinarcurios$skipDraconicJump(final LivingEvent.LivingJumpEvent event, final CallbackInfo ci) {
        if (event.entityLiving instanceof EntityPlayer
            && BagUtils.isWearingInfiniteBackpack((EntityPlayer) event.entityLiving)) {
            ci.cancel();
        }
    }

    @Inject(method = "getBreakSpeed", at = @At("HEAD"), cancellable = true)
    private void elinarcurios$skipDraconicBreakSpeed(final PlayerEvent.BreakSpeed event, final CallbackInfo ci) {
        if (event.entityPlayer != null && BagUtils.isWearingInfiniteBackpack(event.entityPlayer)) {
            ci.cancel();
        }
    }
}
