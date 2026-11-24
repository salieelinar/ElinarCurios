package elinarcurios.util;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import elinarcurios.ElinarCurios;
import elinarcurios.item.ItemInfiniteBackpack;
import elinarcurios.network.SyncBagPacket;
import elinarcurios.player.BackpackBuffHandler;
import elinarcurios.player.BagProperty;

/**
 * Wearing helpers, fully self-contained: the worn backpack lives in the built-in
 * {@link BagProperty} player property (synced to the client for rendering).
 */
public final class BagUtils {

    private BagUtils() {}

    /** The worn infinite backpack, if any (server side, or client when the property is synced). */
    public static ItemStack getWornInfiniteBackpack(final EntityPlayer player) {
        final BagProperty prop = BagProperty.get(player);
        if (prop == null) return null;
        final ItemStack worn = prop.getWearable();
        return worn != null && worn.getItem() instanceof ItemInfiniteBackpack ? worn : null;
    }

    public static boolean isWearingInfiniteBackpack(final EntityPlayer player) {
        return getWornInfiniteBackpack(player) != null;
    }

    /** Client-side wearing check (the property is synced on equip/unequip/login). */
    public static boolean clientIsWearingInfiniteBackpack(final EntityPlayer player) {
        return isWearingInfiniteBackpack(player);
    }

    /** Equip the held backpack. Returns true on success. */
    public static boolean equip(final EntityPlayer player) {
        final ItemStack held = player.getHeldItem();
        if (held == null || !(held.getItem() instanceof ItemInfiniteBackpack)) return false;
        final BagProperty prop = BagProperty.get(player);
        if (prop == null || prop.getWearable() != null) return false;

        prop.setWearable(held.copy());
        // The wearable is a copy; remove the original from the hand and force an
        // inventory sync so the client stops showing it in the hotbar.
        player.inventory.setInventorySlotContents(player.inventory.currentItem, null);
        player.inventoryContainer.detectAndSendChanges();
        syncWornBagToClient(player);
        return true;
    }

    public static void unequip(final EntityPlayer player) {
        final BagProperty prop = BagProperty.get(player);
        if (prop == null) return;
        final ItemStack worn = prop.getWearable();
        if (worn == null || !(worn.getItem() instanceof ItemInfiniteBackpack)) return;

        // Stop the buff-page potion effects immediately (server side; no-op on the client).
        BackpackBuffHandler.removeAllFor(player);
        prop.setWearable(null);
        if (!player.inventory.addItemStackToInventory(worn)) {
            player.dropPlayerItemWithRandomChoice(worn, false);
        }
        player.inventoryContainer.detectAndSendChanges();
        syncWornBagToClient(player);
    }

    /** Send the worn backpack to the client (needed for back rendering and the hotkey check). */
    public static void syncWornBagToClient(final EntityPlayer player) {
        if (player instanceof EntityPlayerMP) {
            final NBTTagCompound data = new NBTTagCompound();
            final BagProperty prop = BagProperty.get(player);
            if (prop != null) prop.saveNBTData(data);
            ((EntityPlayerMP) player).playerNetServerHandler.sendPacket(
                ElinarCurios.networkWrapper.getPacketFrom(new SyncBagPacket.Message(player.getEntityId(), data)));
        }
    }
}
