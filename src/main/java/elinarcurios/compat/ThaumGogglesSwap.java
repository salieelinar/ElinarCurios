package elinarcurios.compat;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import elinarcurios.item.ItemVirtualGoggles;
import elinarcurios.util.BagSettings;
import elinarcurios.util.BagUtils;
import thaumcraft.api.IGoggles;
import thaumcraft.api.nodes.IRevealer;

/**
 * Temporarily puts the virtual goggles stack into the helmet slot for the duration of a single
 * Thaumcraft render call, then restores the real helmet.
 *
 * <p>
 * This is the "force the detection" path: Thaumcraft's block overlay and node renderer both read
 * {@code inventory.armorItemInSlot(3)}; swapping the slot makes them see goggles no matter which
 * real helmet is worn, while the real helmet keeps rendering (armour rendering reads the array in
 * its own call, outside of these two methods). A real goggles item is left untouched.
 */
public final class ThaumGogglesSwap {

    private static final ThreadLocal<Object[]> CURRENT = new ThreadLocal<Object[]>();

    private ThaumGogglesSwap() {}

    public static void begin(final EntityPlayer player) {
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
        CURRENT.set(new Object[] { player, real });
    }

    public static void end() {
        final Object[] saved = CURRENT.get();
        if (saved == null) return;
        CURRENT.remove();

        final EntityPlayer player = (EntityPlayer) saved[0];
        final ItemStack real = (ItemStack) saved[1];
        final ItemStack current = player.inventory.armorInventory[3];
        if (current != null && current.getItem() == ItemVirtualGoggles.instance) {
            player.inventory.armorInventory[3] = real;
        }
    }
}
