package elinarcurios.mixin;

import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingEvent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import elinarcurios.compat.GSSpaceSuitCompat;

/**
 * Server side of the space-suit bridge: GalaxySpace applies suffocation/atmosphere debuffs for
 * every missing suit piece, so the backpack answers "4 pieces worn" while it is worn.
 */
@Mixin(targets = "galaxyspace.core.handler.GSEventHandler", remap = false)
public abstract class MixinGSEventHandler {

    private static final int SUIT_PIECES = 4;

    @Inject(method = "onEntityUpdate", at = @At("HEAD"))
    private void elinarcurios$rememberEntity(final LivingEvent.LivingUpdateEvent event, final CallbackInfo ci) {
        GSSpaceSuitCompat.remember(event.entityLiving);
    }

    @Inject(method = "onEntityUpdate", at = @At("RETURN"))
    private void elinarcurios$forgetEntity(final LivingEvent.LivingUpdateEvent event, final CallbackInfo ci) {
        GSSpaceSuitCompat.forget();
    }

    @Redirect(
        method = "onEntityUpdate",
        at = @At(
            value = "INVOKE",
            target = "Lgalaxyspace/core/handler/GSSpaceSuitHandler;getSpaceSuitCount(Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemStack;)I"))
    private int elinarcurios$spaceSuitCount(final ItemStack helmet, final ItemStack chest, final ItemStack leggings,
        final ItemStack boots) {
        if (GSSpaceSuitCompat.wearingBackpackHere(null)) return SUIT_PIECES;
        return GSSpaceSuitCompat.originalCount(helmet, chest, leggings, boots);
    }
}
