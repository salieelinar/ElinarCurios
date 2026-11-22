package elinarcurios.mixin;

import net.minecraft.entity.player.EntityPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import elinarcurios.util.BagUtils;

/** Client side: GalaxySpace's "atmospheric armour" check is a full 4-piece suit query. */
@Mixin(targets = "galaxyspace.core.handler.GSClientEventHandler", remap = false)
public abstract class MixinGSClientEventHandler {

    @Inject(method = "getAtmoArmor", at = @At("HEAD"), cancellable = true)
    private static void elinarcurios$atmoArmor(final EntityPlayer player, final CallbackInfoReturnable<Boolean> cir) {
        if (player != null && BagUtils.isWearingInfiniteBackpack(player)) {
            cir.setReturnValue(Boolean.TRUE);
        }
    }
}
