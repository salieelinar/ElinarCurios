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
import elinarcurios.util.ElinarProbe;

/**
 * Waila's Thaumcraft addon only sends aspect data to the client when the queried player wears
 * real goggles: {@code HUDHandlerIAspectContainer.getNBTData} reads
 * {@code player.inventory.armorInventory[3]} directly and returns early when it is null.
 *
 * <p>
 * Redirecting that array read is enough: Waila receives a version of the array whose helmet slot
 * contains Thaumcraft's own goggles stack, so its regular goggles check passes and the aspect
 * lines show up in the tooltip exactly like they do with real goggles.
 */
@Mixin(targets = "mcp.mobius.waila.addons.thaumcraft.HUDHandlerIAspectContainer", remap = false)
public abstract class MixinWailaAspectHandler {

    @Redirect(
        method = "getNBTData",
        at = @At(
            value = "GETFIELD",
            target = "Lnet/minecraft/entity/player/InventoryPlayer;field_70460_b:[Lnet/minecraft/item/ItemStack;"))
    private static ItemStack[] elinarcurios$wailaGoggles(final InventoryPlayer inventory) {
        final ItemStack[] original = inventory.armorInventory;
        ElinarProbe.WAILA.incrementAndGet();
        try {
            final EntityPlayer player = inventory.player;
            if (player != null) {
                final ItemStack bag = BagUtils.getWornInfiniteBackpack(player);
                if (bag != null && BagSettings.getBoolean(bag, BagSettings.REVEAL)) {
                    final ItemStack goggles = ThaumGogglesCompat.goggles();
                    if (goggles != null) {
                        ElinarProbe.WAILA_FORCED.incrementAndGet();
                        ElinarProbe.dump();
                        final ItemStack[] copy = original.clone();
                        copy[3] = goggles;
                        return copy;
                    }
                }
            }
        } catch (Throwable ignored) {}
        ElinarProbe.dump();
        return original;
    }
}
