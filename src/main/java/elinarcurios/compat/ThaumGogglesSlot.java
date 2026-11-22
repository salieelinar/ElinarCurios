package elinarcurios.compat;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import elinarcurios.item.ItemVirtualGoggles;
import elinarcurios.util.BagSettings;

/**
 * Keeps a {@link ItemVirtualGoggles} in the wearer's (otherwise empty) helmet slot while the
 * Infinite Backpack is worn and at least one of the two reveal toggles is on.
 *
 * <p>
 * Everything that asks "is the player wearing goggles?" - Thaumcraft's block overlay, its node
 * renderer and Waila's Thaumcraft addon - reads the helmet slot and only the helmet slot. Placing
 * the same interfaces the Draconic helmet implements there makes all of them work unchanged, with
 * no mixins into either mod. A real helmet always wins: the helper is only inserted into an empty
 * slot and any stray copy is removed from the inventory every tick.
 */
public final class ThaumGogglesSlot {

    private ThaumGogglesSlot() {}

    public static void manage(final EntityPlayer player, final ItemStack bag) {
        if (player == null || player.worldObj == null || player.worldObj.isRemote || player.isDead) return;
        if (ItemVirtualGoggles.instance == null) return;

        final boolean want = bag != null
            && (BagSettings.getBoolean(bag, BagSettings.REVEAL) || BagSettings.getBoolean(bag, BagSettings.NODES));

        final ItemStack[] armor = player.inventory.armorInventory;
        // The helper is virtual: any copy outside the helmet slot is deleted, never dropped.
        for (int i = 0; i < 3; i++) {
            if (isVirtual(armor[i])) armor[i] = null;
        }
        for (int i = 0; i < player.inventory.mainInventory.length; i++) {
            if (isVirtual(player.inventory.mainInventory[i])) player.inventory.mainInventory[i] = null;
        }

        if (!want) {
            if (isVirtual(armor[3])) armor[3] = null;
            return;
        }
        if (armor[3] == null) {
            armor[3] = new ItemStack(ItemVirtualGoggles.instance);
        }
    }

    /** True when the stack is our virtual goggles helper (used to keep it out of death drops). */
    public static boolean isVirtual(final ItemStack stack) {
        return stack != null && stack.getItem() == ItemVirtualGoggles.instance;
    }
}
