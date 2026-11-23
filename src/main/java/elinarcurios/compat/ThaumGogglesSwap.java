package elinarcurios.compat;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import elinarcurios.item.ItemVirtualGoggles;
import elinarcurios.util.BagSettings;
import elinarcurios.util.BagUtils;
import thaumcraft.api.IGoggles;
import thaumcraft.api.nodes.IRevealer;

/**
 * Temporarily puts the virtual goggles stack into the helmet slot for the duration of a render
 * callback, then restores the real helmet.
 *
 * <p>
 * This is the "force the detection" path: Thaumcraft's block overlay, node renderer and anything
 * else that reads {@code inventory.armorItemInSlot(3)} / {@code armorInventory[3]} see goggles no
 * matter which real helmet is worn, while the real helmet keeps rendering (armour rendering happens
 * outside of the hooked tile-entity dispatcher call). A real goggles item is left untouched.
 *
 * <p>
 * Re-entrant safe: an inner begin (e.g. the tile dispatcher wrapping the node renderer) only bumps
 * the depth, and only the outer end restores the slot.
 */
public final class ThaumGogglesSwap {

    private static final ThreadLocal<int[]> DEPTH = new ThreadLocal<int[]>();
    private static final ThreadLocal<Object[]> SAVED = new ThreadLocal<Object[]>();

    private ThaumGogglesSwap() {}

    public static void begin(final EntityPlayer player) {
        int[] depth = DEPTH.get();
        if (depth == null) {
            depth = new int[1];
            DEPTH.set(depth);
        }
        depth[0]++;
        if (depth[0] > 1) return;
        if (player == null || player.worldObj == null || !player.worldObj.isRemote) return;
        if (ItemVirtualGoggles.instance == null) return;

        final ItemStack bag = BagUtils.getWornInfiniteBackpack(player);
        if (bag == null) return;
        if (!BagSettings.getBoolean(bag, BagSettings.REVEAL) && !BagSettings.getBoolean(bag, BagSettings.NODES)) {
            return;
        }

        final ItemStack[] armor = player.inventory.armorInventory;
        final ItemStack real = armor[3];
        if (real != null && (real.getItem() instanceof IGoggles || real.getItem() instanceof IRevealer)) {
            return;
        }
        armor[3] = new ItemStack(ItemVirtualGoggles.instance);
        SAVED.set(new Object[] { player, real });
    }

    public static void end() {
        final int[] depth = DEPTH.get();
        if (depth == null) return;
        depth[0]--;
        if (depth[0] > 0) return;
        DEPTH.remove();

        final Object[] saved = SAVED.get();
        if (saved == null) return;
        SAVED.remove();

        final EntityPlayer player = (EntityPlayer) saved[0];
        final ItemStack real = (ItemStack) saved[1];
        final ItemStack current = player.inventory.armorInventory[3];
        if (current != null && current.getItem() == ItemVirtualGoggles.instance) {
            player.inventory.armorInventory[3] = real;
        }
    }
}
