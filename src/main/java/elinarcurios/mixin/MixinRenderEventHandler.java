package elinarcurios.mixin;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import elinarcurios.compat.ThaumGogglesCompat;
import elinarcurios.util.BagSettings;
import elinarcurios.util.BagUtils;

/**
 * Thaumcraft only looks at the helmet slot when it decides whether to show goggles popups.
 * When the backpack is worn and its "reveal" toggle is on, the helmet read is replaced with
 * Thaumcraft's own goggles stack (so all of TC's logic keeps working untouched).
 */
@Mixin(targets = "thaumcraft.client.lib.RenderEventHandler", remap = false)
public abstract class MixinRenderEventHandler {

    @Redirect(
        method = "blockHighlight",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/entity/player/InventoryPlayer;func_70440_f(I)Lnet/minecraft/item/ItemStack;"))
    private ItemStack elinarcurios$backpackGoggles(final InventoryPlayer inventory, final int slot) {
        final EntityPlayer player = inventory == null ? null : inventory.player;
        if (player != null) {
            final ItemStack bag = BagUtils.getWornInfiniteBackpack(player);
            if (bag != null && BagSettings.getBoolean(bag, BagSettings.REVEAL)) {
                final ItemStack goggles = ThaumGogglesCompat.goggles();
                if (goggles != null) return goggles;
            }
        }
        return inventory.armorItemInSlot(slot);
    }
}
