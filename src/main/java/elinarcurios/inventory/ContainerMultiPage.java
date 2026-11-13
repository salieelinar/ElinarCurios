package elinarcurios.inventory;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ICrafting;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.MathHelper;

import elinarcurios.CommonProxy;
import elinarcurios.ElinarCurios;
import elinarcurios.network.CurrentPagePacket;
import elinarcurios.network.InfiniteBagSlotSync;
import elinarcurios.network.InfiniteBagSyncAllSlots;

/**
 * 多页大容量容器的公共 Container:243 个存储槽(9×27 超宽布局)+ 玩家背包,
 * 支持 int 级大堆叠(Integer.MAX_VALUE)。无尽背包与永恒箱子共用。
 * 逻辑源自 Avaritiaddons 的 ContainerInfinityChest(MPL-2.0, WanionCane)。
 */
public abstract class ContainerMultiPage extends Container {

    public static final int STORAGE_SLOT_COUNT = 243;
    public static final int PLAYER_SLOT_START = STORAGE_SLOT_COUNT; // 243
    public static final int PLAYER_SLOT_END = PLAYER_SLOT_START + 36; // 279

    protected final InventoryPlayer inventoryPlayer;
    protected final IInventory storageInventory;

    protected ContainerMultiPage(final InventoryPlayer inventoryPlayer, final IInventory storageInventory) {
        this.inventoryPlayer = inventoryPlayer;
        this.storageInventory = storageInventory;
        for (int y = 0; y < 9; y++) for (int x = 0; x < 27; x++)
            addSlotToContainer(createStorageSlot(storageInventory, y * 27 + x, 8 + (18 * x), 18 + (18 * y)));
        for (int y = 0; y < 3; y++) for (int x = 0; x < 9; x++)
            addSlotToContainer(new Slot(inventoryPlayer, 9 + y * 9 + x, 170 + (18 * x), 194 + (18 * y)));
        for (int i = 0; i < 9; i++) addSlotToContainer(new Slot(inventoryPlayer, i, 170 + (18 * i), 252));
    }

    /** Storage slot factory; subclasses may return page/view-aware slots. */
    protected Slot createStorageSlot(final IInventory inventory, final int index, final int x, final int y) {
        return new Slot(inventory, index, x, y);
    }

    /**
     * Insert an item into a storage slot. Default: plain put. Subclasses with a
     * whole-chest merge model (Eternal Chest) override this so player clicks and
     * shift-click inserts merge into existing stacks anywhere, exactly like the
     * vanilla Infinity Chest.
     */
    protected void putStackWithMerge(final Slot slot, final ItemStack stack) {
        slot.putStack(stack);
    }

    /** The pageable storage inventory (Item-NBT-backed or TileEntity-backed). */
    protected IInventory getStorageInventory() {
        return storageInventory;
    }

    public abstract int getCurrentPage();

    /** Number of pages of this container (backpack 10, Eternal Chest 12). */
    public abstract int getPageCount();

    /** Client-side: adopt the server-confirmed page. */
    public abstract void syncPage(int page);

    /** Switch the visible page and resync every open window. */
    public abstract void switchPage(int page);

    /** Push the whole slot array to every watching player (used after a page switch). */
    protected final void resyncAllToPlayers() {
        final ItemStack[] stacks = new ItemStack[inventorySlots.size()];
        for (int i = 0; i < inventorySlots.size(); i++) {
            stacks[i] = ((Slot) inventorySlots.get(i)).getStack();
        }
        for (Object crafter : crafters) {
            if (crafter instanceof EntityPlayerMP) {
                ElinarCurios.networkWrapper.sendTo(new InfiniteBagSyncAllSlots(stacks), (EntityPlayerMP) crafter);
            }
        }
    }

    /**
     * When a player opens the GUI, skip the vanilla window-items packet (0x32): its
     * stack counts are byte-sized, so any storage slot holding more than 127 items is
     * corrupted on the client (huge stacks render as negative/empty counts, items look
     * like they vanish, and sorting operates on the corrupted display). Push the full
     * snapshot through the custom int-safe protocol instead, plus the current page.
     */
    @Override
    public void addCraftingToCrafters(final ICrafting crafting) {
        if (crafters.contains(crafting)) return;
        crafters.add(crafting);
        if (crafting instanceof EntityPlayerMP) {
            final EntityPlayerMP player = (EntityPlayerMP) crafting;
            resyncAllToPlayers();
            ElinarCurios.networkWrapper.sendTo(new CurrentPagePacket(getCurrentPage()), player);
        }
        detectAndSendChanges();
    }

    @Override
    public boolean canInteractWith(final EntityPlayer entityPlayer) {
        return getStorageInventory().isUseableByPlayer(entityPlayer);
    }

    @Override
    public ItemStack transferStackInSlot(final EntityPlayer entityPlayer, final int slotIndex) {
        ItemStack itemstack = null;
        final Slot actualSlot = (Slot) this.inventorySlots.get(slotIndex);
        if (actualSlot != null && actualSlot.getHasStack()) {
            ItemStack itemstack1 = actualSlot.getStack();
            itemstack = itemstack1.copy();
            if (slotIndex > 242) {
                if (!mergeItemStack(itemstack1, 0, 243, false)) return null;
            } else if (!mergeItemStack(itemstack1, 243, 279, true)) return null;
            if (itemstack1.stackSize == 0) actualSlot.putStack(null);
            else actualSlot.onSlotChanged();
        }
        return itemstack;
    }

    /** Server-side merge that allows stacking up to Integer.MAX_VALUE into a single slot. */
    @Override
    public boolean mergeItemStack(final ItemStack itemStack, final int start, final int end, final boolean backwards) {
        if (CommonProxy.isClient()) return false;
        int currentSlot = !backwards ? start : end - 1;
        boolean someThingChanged = false;

        while (itemStack.stackSize > 0 && (!backwards && currentSlot < end || backwards && currentSlot >= start)) {
            final Slot slot = (Slot) inventorySlots.get(currentSlot);
            final ItemStack slotStack = slot.getStack();
            boolean changed = false;

            if (!backwards) {
                if (slotStack == null) {
                    if (slot.isItemValid(itemStack)) {
                        putStackWithMerge(slot, itemStack.copy());
                        itemStack.stackSize = 0;
                        changed = true;
                    }
                } else if (slotStack.getItem() == itemStack.getItem()
                    && itemStack.getItemDamage() == slotStack.getItemDamage()
                    && ItemStack.areItemStackTagsEqual(itemStack, slotStack)) {
                        if ((long) slotStack.stackSize + (long) itemStack.stackSize <= (long) Integer.MAX_VALUE) {
                            slotStack.stackSize += itemStack.stackSize;
                            itemStack.stackSize = 0;
                            changed = true;
                        }
                    }
                ++currentSlot;
            } else {
                if (slotStack == null) {
                    if (slot.isItemValid(itemStack)) {
                        final int dif = MathHelper.clamp_int(itemStack.getMaxStackSize(), 1, itemStack.stackSize);
                        final ItemStack newItemStack = itemStack.copy();
                        newItemStack.stackSize = dif;
                        itemStack.stackSize -= dif;
                        slot.putStack(newItemStack);
                        changed = true;
                    }
                } else
                    if (slotStack.getItem() == itemStack.getItem() && slotStack.stackSize < slotStack.getMaxStackSize()
                        && itemStack.getItemDamage() == slotStack.getItemDamage()
                        && ItemStack.areItemStackTagsEqual(itemStack, slotStack)) {
                            final int dif = MathHelper
                                .clamp_int(itemStack.stackSize, 1, slotStack.getMaxStackSize() - slotStack.stackSize);
                            slotStack.stackSize += dif;
                            itemStack.stackSize -= dif;
                            changed = true;
                        }
                --currentSlot;
            }
            if (changed) {
                slot.onSlotChanged();
                someThingChanged = true;
            }
        }
        return someThingChanged;
    }

    @Override
    public boolean canDragIntoSlot(final Slot slot) {
        return slot.slotNumber > 242;
    }

    /** Custom slotClick handling big stacks, ported from ContainerInfinityChest. */
    @Override
    public ItemStack slotClick(int slot, final int mouseButton, final int modifier, final EntityPlayer entityPlayer) {
        if (slot >= 0 && slot < 243 && modifier == 0) {
            final Slot actualSlot = (Slot) inventorySlots.get(slot);
            final ItemStack slotStack = actualSlot.getStack();
            final ItemStack playerStack = entityPlayer.inventory.getItemStack();
            if (mouseButton == 0) {
                if (slotStack == null && playerStack != null) {
                    if (actualSlot.isItemValid(playerStack)) {
                        putStackWithMerge(actualSlot, playerStack.copy());
                        entityPlayer.inventory.setItemStack(null);
                    }
                } else if (slotStack != null && playerStack == null) {
                    final ItemStack newItemStack = slotStack.copy();
                    int took = MathHelper.clamp_int(slotStack.stackSize, 1, newItemStack.getMaxStackSize());
                    newItemStack.stackSize = took;
                    slotStack.stackSize -= took;
                    entityPlayer.inventory.setItemStack(newItemStack);
                    if (slotStack.stackSize == 0) actualSlot.putStack(null);
                    else actualSlot.onSlotChanged();
                } else if (slotStack != null && slotStack.getItem() == playerStack.getItem()
                    && playerStack.getItemDamage() == slotStack.getItemDamage()
                    && ItemStack.areItemStackTagsEqual(playerStack, slotStack)) {
                        if ((long) slotStack.stackSize + (long) playerStack.stackSize <= (long) Integer.MAX_VALUE) {
                            slotStack.stackSize += playerStack.stackSize;
                            entityPlayer.inventory.setItemStack(null);
                        }
                    }
            } else if (mouseButton == 1 && playerStack != null) {
                if (slotStack == null) {
                    if (actualSlot.isItemValid(playerStack)) {
                        playerStack.stackSize--;
                        final ItemStack newItemStack = playerStack.copy();
                        newItemStack.stackSize = 1;
                        putStackWithMerge(actualSlot, newItemStack);
                        if (playerStack.stackSize == 0) entityPlayer.inventory.setItemStack(null);
                    }
                } else if (slotStack.getItem() == playerStack.getItem()
                    && playerStack.getItemDamage() == slotStack.getItemDamage()
                    && ItemStack.areItemStackTagsEqual(playerStack, slotStack)) {
                        playerStack.stackSize--;
                        slotStack.stackSize++;
                        actualSlot.onSlotChanged();
                        if (playerStack.stackSize == 0) entityPlayer.inventory.setItemStack(null);
                    }
            } else if (mouseButton == 1 && slotStack != null) {
                int took = slotStack.stackSize > slotStack.getMaxStackSize() ? slotStack.getMaxStackSize() / 2
                    : slotStack.stackSize / 2;
                if (took == 0) took = 1;
                final ItemStack newItemStack = slotStack.copy();
                newItemStack.stackSize = took;
                slotStack.stackSize -= took;
                entityPlayer.inventory.setItemStack(newItemStack);
                if (slotStack.stackSize == 0) actualSlot.putStack(null);
                else actualSlot.onSlotChanged();
            }
            return actualSlot.getHasStack() ? actualSlot.getStack()
                .copy() : null;
        } else if (slot >= 0 && slot < 243 && modifier == 2) return null;
        else return super.slotClick(slot, mouseButton, modifier, entityPlayer);
    }

    /** Send slot changes with int-sized stack counts (vanilla packets clamp to short). */
    @Override
    public void detectAndSendChanges() {
        if (crafters.isEmpty()) return;
        for (int i = 0; i < inventorySlots.size(); ++i) {
            final ItemStack itemstack = ((Slot) inventorySlots.get(i)).getStack();
            final ItemStack itemstack1 = (ItemStack) inventoryItemStacks.get(i);

            if (!ItemStack.areItemStacksEqual(itemstack1, itemstack)) {
                inventoryItemStacks.set(i, itemstack == null ? null : itemstack.copy());

                final NBTTagCompound nbtTagCompound = new NBTTagCompound();
                nbtTagCompound.setShort("Slot", (short) i);
                if (itemstack != null) nbtTagCompound.setInteger("intCount", itemstack.stackSize);
                for (Object crafter : crafters) if (crafter instanceof EntityPlayerMP)
                    ElinarCurios.networkWrapper.sendTo(new InfiniteBagSlotSync(itemstack, i), (EntityPlayerMP) crafter);
            }
        }
    }

    public void syncData(final ItemStack itemStack, final int slot, final int stackSize) {
        if (itemStack != null) {
            itemStack.stackSize = stackSize;
            ((Slot) inventorySlots.get(slot)).putStack(itemStack);
        } else((Slot) inventorySlots.get(slot)).putStack(null);
    }

    @Override
    public void onContainerClosed(final EntityPlayer entityPlayer) {
        super.onContainerClosed(entityPlayer);
        getStorageInventory().closeInventory();
    }
}
