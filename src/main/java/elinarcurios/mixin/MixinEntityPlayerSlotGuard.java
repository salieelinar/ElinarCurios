package elinarcurios.mixin;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import elinarcurios.Config;
import elinarcurios.util.BagUtils;
import elinarcurios.util.DisarmGuard;

/**
 * Blocks forced held-item clears ("disarm") for backpack wearers.
 *
 * <p>
 * The known disarming implementations (Witchery's Expelliarmus symbol, and anything else built the
 * same way) first spawn the held stack as a world item and then call
 * {@code setCurrentItemOrArmor(0, null)}. Blanket-cancelling that clear would duplicate the stack,
 * so it is only cancelled when {@link DisarmGuard} recorded the matching just-spawned item entity in
 * the same tick; that entity is removed by the guard, leaving exactly one copy in the player's hand.
 * Armour slots (1-4) are untouched so normal equipping works.
 */
@Mixin(EntityPlayer.class)
public abstract class MixinEntityPlayerSlotGuard {

    @Inject(method = "setCurrentItemOrArmor", at = @At("HEAD"), cancellable = true)
    private void elinarcurios$guardDisarm(final int slot, final ItemStack stack, final CallbackInfo ci) {
        if (!Config.protectInfiniteBackpack) return;
        if (slot != 0 || stack != null) return;
        final EntityPlayer self = (EntityPlayer) (Object) this;
        if (!BagUtils.isWearingInfiniteBackpack(self)) return;
        if (DisarmGuard.consume(self)) ci.cancel();
    }
}
