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
 * Node rendering: Thaumcraft checks the helmet slot for {@code IRevealer}. Same trick as the
 * goggles overlay - hand it its own goggles stack when the backpack's "nodes" toggle is on.
 */
@Mixin(targets = "thaumcraft.client.renderers.tile.TileNodeRenderer", remap = false)
public abstract class MixinTileNodeRenderer {

    @Redirect(
        method = "func_147500_a",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/entity/player/InventoryPlayer;func_70440_f(I)Lnet/minecraft/item/ItemStack;"))
    private ItemStack elinarcurios$backpackRevealer(final InventoryPlayer inventory, final int slot) {
        final EntityPlayer player = inventory == null ? null : inventory.player;
        if (player != null) {
            final ItemStack bag = BagUtils.getWornInfiniteBackpack(player);
            if (bag != null && BagSettings.getBoolean(bag, BagSettings.NODES)) {
                final ItemStack goggles = ThaumGogglesCompat.goggles();
                if (goggles != null) return goggles;
            }
        }
        return inventory.armorItemInSlot(slot);
    }
}
