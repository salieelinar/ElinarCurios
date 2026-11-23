package elinarcurios.compat;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import elinarcurios.item.ItemVirtualGoggles;
import elinarcurios.util.BagSettings;
import elinarcurios.util.BagUtils;
import elinarcurios.util.GogglesState;
import thaumcraft.api.IGoggles;
import thaumcraft.api.nodes.IRevealer;

/**
 * Temporarily puts the virtual goggles stack into the helmet slot for the duration of a render
 * callback, then restores the real helmet.
 *
 * <p>
 * The expensive checks (extended-property lookup, two NBT reads, real-goggles instanceof checks)
 * are folded into one {@link #refresh} per client tick; the render path ({@link #begin}/{@link
 * #end}) only reads cached fields and swaps the slot. A single ItemStack instance is reused for
 * every swap, so the render loop does not allocate.
 *
 * <p>
 * Re-entrant safe: an inner begin (e.g. the block-highlight hook nested in the tile dispatcher)
 * only bumps the depth, and only the outer end restores the slot.
 */
public final class ThaumGogglesSwap {

    private static final ThreadLocal<int[]> DEPTH = new ThreadLocal<int[]>();
    private static final ThreadLocal<Object[]> SAVED = new ThreadLocal<Object[]>();

    private ThaumGogglesSwap() {}

    /** Called once per client tick: decides whether a swap is needed and prepares the stack. */
    public static void refresh(final EntityPlayer player) {
        boolean ok = false;
        ItemStack swap = null;
        if (player != null && player.worldObj != null
            && player.worldObj.isRemote
            && ItemVirtualGoggles.instance != null) {
            final ItemStack bag = BagUtils.getWornInfiniteBackpack(player);
            if (bag != null && (BagSettings.getBoolean(bag, BagSettings.REVEAL)
                || BagSettings.getBoolean(bag, BagSettings.NODES))) {
                final ItemStack real = player.inventory.armorInventory[3];
                if (real == null || !(real.getItem() instanceof IGoggles || real.getItem() instanceof IRevealer)) {
                    swap = new ItemStack(ItemVirtualGoggles.instance);
                    ok = true;
                }
            }
        }
        GogglesState.swapStack = swap;
        GogglesState.active = ok;
    }

    public static void begin(final EntityPlayer player) {
        if (!GogglesState.active) return;
        int[] depth = DEPTH.get();
        if (depth == null) {
            depth = new int[1];
            DEPTH.set(depth);
        }
        depth[0]++;
        if (depth[0] > 1) return;

        final ItemStack swap = GogglesState.swapStack;
        if (swap == null || player == null || player.worldObj == null || !player.worldObj.isRemote) return;
        final ItemStack[] armor = player.inventory.armorInventory;
        SAVED.set(new Object[] { player, armor[3] });
        armor[3] = swap;
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
