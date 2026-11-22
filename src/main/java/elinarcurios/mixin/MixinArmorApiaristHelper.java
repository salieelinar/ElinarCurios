package elinarcurios.mixin;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import elinarcurios.util.BagUtils;

/**
 * Forestry bees ask {@code ArmorApiaristHelper} whether the target is protected and how many
 * protective pieces it wears. A worn backpack counts as a full set (4 pieces), which is what
 * Draconic Evolution's armour did.
 */
@Mixin(targets = "forestry.apiculture.ArmorApiaristHelper", remap = false)
public abstract class MixinArmorApiaristHelper {

    private static final int BACKPACK_ARMOR_PIECES = 4;

    @Inject(
        method = "isArmorApiarist(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/EntityLivingBase;Ljava/lang/String;Z)Z",
        at = @At("HEAD"),
        cancellable = true)
    private void elinarcurios$apiaristLiving(final ItemStack stack, final EntityLivingBase entity, final String cause,
        final boolean consume, final CallbackInfoReturnable<Boolean> cir) {
        if (isBackpackWearer(entity)) cir.setReturnValue(Boolean.TRUE);
    }

    @Inject(
        method = "isArmorApiarist(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/player/EntityPlayer;Ljava/lang/String;Z)Z",
        at = @At("HEAD"),
        cancellable = true)
    private void elinarcurios$apiaristPlayer(final ItemStack stack, final EntityPlayer player, final String cause,
        final boolean consume, final CallbackInfoReturnable<Boolean> cir) {
        if (isBackpackWearer(player)) cir.setReturnValue(Boolean.TRUE);
    }

    @Inject(
        method = "wearsItems(Lnet/minecraft/entity/EntityLivingBase;Ljava/lang/String;Z)I",
        at = @At("HEAD"),
        cancellable = true)
    private void elinarcurios$apiaristCount(final EntityLivingBase entity, final String cause, final boolean consume,
        final CallbackInfoReturnable<Integer> cir) {
        if (isBackpackWearer(entity)) cir.setReturnValue(Integer.valueOf(BACKPACK_ARMOR_PIECES));
    }

    @Inject(
        method = "wearsItems(Lnet/minecraft/entity/player/EntityPlayer;Ljava/lang/String;Z)I",
        at = @At("HEAD"),
        cancellable = true)
    private void elinarcurios$apiaristPlayerCount(final EntityPlayer player, final String cause, final boolean consume,
        final CallbackInfoReturnable<Integer> cir) {
        if (isBackpackWearer(player)) cir.setReturnValue(Integer.valueOf(BACKPACK_ARMOR_PIECES));
    }

    private static boolean isBackpackWearer(final EntityLivingBase entity) {
        return entity instanceof EntityPlayer && BagUtils.isWearingInfiniteBackpack((EntityPlayer) entity);
    }
}
