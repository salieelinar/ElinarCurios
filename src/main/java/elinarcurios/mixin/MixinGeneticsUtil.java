package elinarcurios.mixin;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import elinarcurios.util.BagSettings;
import elinarcurios.util.BagUtils;

/** Forestry's "naturalist's eye" (pollination info) is an armour query; the backpack provides it. */
@Mixin(targets = "forestry.core.utils.GeneticsUtil", remap = false)
public abstract class MixinGeneticsUtil {

    @Inject(method = "hasNaturalistEye", at = @At("HEAD"), cancellable = true)
    private static void elinarcurios$naturalistEye(final EntityPlayer player,
        final CallbackInfoReturnable<Boolean> cir) {
        if (player == null) return;
        final ItemStack bag = BagUtils.getWornInfiniteBackpack(player);
        if (bag != null && BagSettings.getBoolean(bag, BagSettings.NATURALIST)) {
            cir.setReturnValue(Boolean.TRUE);
        }
    }
}
