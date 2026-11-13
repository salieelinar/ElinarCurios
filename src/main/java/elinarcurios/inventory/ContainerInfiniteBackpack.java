package elinarcurios.inventory;

import javax.annotation.Nonnull;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

import elinarcurios.item.ItemInfiniteBackpack;

/**
 * Container for the Infinite Backpack: 243 storage slots + player inventory.
 * Shared big-stack logic lives in {@link ContainerMultiPage}.
 */
public final class ContainerInfiniteBackpack extends ContainerMultiPage {

    public final InventoryInfiniteBackpack inventory;

    public ContainerInfiniteBackpack(@Nonnull final ItemStack bagStack, final InventoryPlayer inventoryPlayer) {
        super(inventoryPlayer, new InventoryInfiniteBackpack(bagStack, ItemInfiniteBackpack.getCurrentPage(bagStack)));
        this.inventory = (InventoryInfiniteBackpack) storageInventory;
        inventory.openInventory();
    }

    public ItemStack getBagStack() {
        return inventory.getContainerStack();
    }

    @Override
    public int getCurrentPage() {
        return inventory.getPageIndex();
    }

    @Override
    public int getPageCount() {
        return ItemInfiniteBackpack.PAGE_COUNT;
    }

    /** Client-side: adopt the server-confirmed page. */
    @Override
    public void syncPage(final int page) {
        inventory.syncPage(page);
    }

    /**
     * Switch the visible page: the previous page is saved into NBT, the new one is
     * loaded into the same slot array and every open window is fully resynced.
     */
    @Override
    public void switchPage(final int page) {
        inventory.switchPage(page);
        resyncAllToPlayers();
    }
}
