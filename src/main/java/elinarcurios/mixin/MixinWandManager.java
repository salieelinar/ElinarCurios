package elinarcurios.mixin;

import net.minecraft.entity.player.EntityPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import elinarcurios.util.BagUtils;
import thaumcraft.api.aspects.Aspect;

/**
 * Thaumcraft sums vis discounts from every {@code IVisDiscountGear} in the inventory / armour.
 * A worn Infinite Backpack replaces the whole draconic set (4 x 15%), so it contributes 60%.
 * The result is clamped to the 90% hard cap Thaumcraft itself uses.
 */
@Mixin(targets = "thaumcraft.common.items.wands.WandManager", remap = false)
public abstract class MixinWandManager {

    private static final float BACKPACK_VIS_DISCOUNT = 60F;
    private static final float VIS_DISCOUNT_CAP = 90F;

    @Inject(method = "getTotalVisDiscount", at = @At("RETURN"), cancellable = true)
    private static void elinarcurios$backpackVisDiscount(final EntityPlayer player, final Aspect aspect,
        final CallbackInfoReturnable<Float> cir) {
        if (player == null || !BagUtils.isWearingInfiniteBackpack(player)) return;
        final float value = cir.getReturnValueF() + BACKPACK_VIS_DISCOUNT;
        cir.setReturnValue(value > VIS_DISCOUNT_CAP ? VIS_DISCOUNT_CAP : value);
    }
}
