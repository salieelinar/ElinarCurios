package elinarcurios.inventory;

import javax.annotation.Nonnull;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ICrafting;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import elinarcurios.ElinarCurios;
import elinarcurios.network.CurrentPagePacket;
import elinarcurios.network.InfiniteBagSyncAllSlots;
import elinarcurios.tile.TileEntityEternalChest;

/**
 * Container for the Eternal Chest: the 243 visible slots map to the <b>currently open
 * page</b> of the whole-chest storage (page * 243 + slotInPage), so the pages are views
 * over one shared 2916-slot inventory.
 *
 * <p>
 * <b>Client display model</b>: slots carry a local stack cache driven by the server
 * (vanilla 1.7.10 {@link Slot#getStack()} reads the inventory directly, but the client-side
 * TileEntity storage is never synced from the server — reading it would show stale/empty
 * data). Therefore {@link EternalChestSlot} keeps its own local cache, the server pushes
 * full slot snapshots via {@link InfiniteBagSyncAllSlots} when the GUI opens and on page
 * switch, and the client never reads the TileEntity's pageCache for display.
 */
public final class ContainerEternalChest extends ContainerMultiPage {

    public final TileEntityEternalChest chest;

    public ContainerEternalChest(@Nonnull final TileEntityEternalChest chest, final InventoryPlayer inventoryPlayer) {
        super(inventoryPlayer, chest);
        this.chest = chest;
        chest.openInventory();
    }

    @Override
    protected Slot createStorageSlot(final IInventory inventory, final int index, final int x, final int y) {
        // NB: must use the constructor argument, not this.chest — createStorageSlot is called
        // from the super constructor, before this.chest is assigned (Java init-order trap).
        return new EternalChestSlot((TileEntityEternalChest) inventory, index, x, y);
    }

    @Override
    public int getCurrentPage() {
        return chest.getPageIndex();
    }

    @Override
    protected void putStackWithMerge(final Slot slot, final ItemStack stack) {
        if (slot instanceof EternalChestSlot) ((EternalChestSlot) slot).insertStack(stack);
        else slot.putStack(stack);
    }

    @Override
    public int getPageCount() {
        return TileEntityEternalChest.PAGE_COUNT;
    }

    /** Client-side: adopt the server-confirmed page. */
    @Override
    public void syncPage(final int page) {
        chest.syncPage(page);
    }

    /** Switch the visible page and resync every open window. */
    @Override
    public void switchPage(final int page) {
        chest.switchPage(page);
        refreshSlotCaches();
        resyncAllToPlayers();
    }

    /**
     * When a player opens the GUI, the vanilla window-item packet (short stack sizes)
     * would corrupt our int-sized stacks — skip it and push the full snapshot through the
     * custom int-safe protocol instead, plus the authoritative current page.
     */
    @Override
    public void addCraftingToCrafters(final ICrafting crafting) {
        if (crafters.contains(crafting)) return;
        crafters.add(crafting);
        if (crafting instanceof EntityPlayerMP) {
            final EntityPlayerMP player = (EntityPlayerMP) crafting;
            refreshSlotCaches();
            ElinarCurios.networkWrapper.sendTo(new InfiniteBagSyncAllSlots(getAllSlotStacks()), player);
            ElinarCurios.networkWrapper.sendTo(new CurrentPagePacket(getCurrentPage()), player);
        }
        detectAndSendChanges();
    }

    /** Reload the server-side slot caches from the currently visible page. */
    private void refreshSlotCaches() {
        for (int i = 0; i < STORAGE_SLOT_COUNT; i++) {
            ((Slot) inventorySlots.get(i))
                .putStack(chest.getStackInSlot(chest.getPageIndex() * TileEntityEternalChest.STORAGE_SLOTS + i));
        }
    }

    /**
     * Bulk storage change (e.g. the Void Chest's solidify): reload every slot cache
     * from the storage and push the full snapshot to every watching player, so open
     * GUIs reflect the new counts immediately.
     */
    public void refreshAndResyncAll() {
        refreshSlotCaches();
        resyncAllToPlayers();
    }

    private ItemStack[] getAllSlotStacks() {
        final ItemStack[] stacks = new ItemStack[inventorySlots.size()];
        for (int i = 0; i < inventorySlots.size(); i++) {
            stacks[i] = ((Slot) inventorySlots.get(i)).getStack();
        }
        return stacks;
    }

    /**
     * Slot bound to a fixed slot-within-page; the actual storage index follows the
     * currently open page. Display uses a local stack cache (server-driven), never the
     * client TileEntity storage.
     */
    private static final class EternalChestSlot extends Slot {

        private final TileEntityEternalChest chest;
        private final int slotInPage;
        private ItemStack localStack;

        private EternalChestSlot(final TileEntityEternalChest chest, final int slotInPage, final int x, final int y) {
            super(chest, slotInPage, x, y);
            this.chest = chest;
            this.slotInPage = slotInPage;
        }

        private int globalSlot() {
            return chest.getPageIndex() * TileEntityEternalChest.STORAGE_SLOTS + slotInPage;
        }

        @Override
        public ItemStack getStack() {
            return localStack;
        }

        /** Sync push / cache refresh: mirror the server exactly, no merge. */
        @Override
        public void putStack(final ItemStack stack) {
            localStack = stack;
            chest.rawSetStack(globalSlot(), stack);
            this.onSlotChanged();
        }

        /**
         * Insert with the whole-chest merge (hopper / player drop path): the stack merges
         * into an existing same-item stack anywhere in the 12 pages; the slot then shows
         * whatever the storage actually holds in this view slot.
         */
        public void insertStack(final ItemStack stack) {
            chest.setInventorySlotContents(globalSlot(), stack);
            localStack = chest.getStackInSlot(globalSlot());
            this.onSlotChanged();
        }

        @Override
        public ItemStack decrStackSize(final int amount) {
            if (localStack != null) {
                final ItemStack result;
                if (localStack.stackSize <= amount) {
                    result = localStack;
                    localStack = null;
                    chest.rawSetStack(globalSlot(), null);
                } else {
                    result = localStack.splitStack(amount);
                    chest.rawSetStack(globalSlot(), localStack);
                }
                this.onSlotChanged();
                return result;
            }
            return null;
        }

        @Override
        public boolean isItemValid(final ItemStack stack) {
            return chest.isItemValidForSlot(slotInPage, stack);
        }
    }
}
