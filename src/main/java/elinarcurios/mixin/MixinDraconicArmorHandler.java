package elinarcurios.mixin;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import cpw.mods.fml.common.gameevent.TickEvent;
import elinarcurios.util.BagUtils;

/**
 * Draconic Evolution co-existence: while the Infinite Backpack is worn, DE's armour handler goes
 * dormant. Without this the DE shield would still drain its points and play hit sounds on every
 * attack (the backpack cancels the damage later in the pipe), its death handler would overwrite
 * the health value, and its movement handling would fight the backpack's settings (flight speed,
 * double jump, x25 mining, step height). Taking the backpack off lets DE take over again on the
 * next tick.
 */
@Mixin(targets = "com.brandon3055.draconicevolution.common.items.armor.CustomArmorHandler", remap = false)
public abstract class MixinDraconicArmorHandler {

    @Inject(method = "onLivingAttack", at = @At("HEAD"), cancellable = true)
    private void elinarcurios$skipDraconicAttack(final LivingAttackEvent event, final CallbackInfo ci) {
        if (event.entityLiving instanceof EntityPlayer
            && BagUtils.isWearingInfiniteBackpack((EntityPlayer) event.entityLiving)) {
            ci.cancel();
        }
    }

    @Inject(method = "onLivingDeath", at = @At("HEAD"), cancellable = true)
    private void elinarcurios$skipDraconicDeath(final LivingDeathEvent event, final CallbackInfo ci) {
        if (event.entityLiving instanceof EntityPlayer
            && BagUtils.isWearingInfiniteBackpack((EntityPlayer) event.entityLiving)) {
            ci.cancel();
        }
    }

    @Inject(method = "onPlayerTick", at = @At("HEAD"), cancellable = true)
    private void elinarcurios$skipDraconicTick(final TickEvent.PlayerTickEvent event, final CallbackInfo ci) {
        if (event.player != null && BagUtils.isWearingInfiniteBackpack(event.player)) {
            ci.cancel();
        }
    }
}
