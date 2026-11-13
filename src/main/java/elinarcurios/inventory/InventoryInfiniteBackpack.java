package elinarcurios.inventory;

import java.util.Arrays;

import javax.annotation.Nonnull;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.MathHelper;
import net.minecraftforge.common.util.Constants.NBT;

import elinarcurios.item.ItemInfiniteBackpack;

/**
 * Item-NBT backed inventory with 243 slots per page (each slot up to
 * {@link Integer#MAX_VALUE} items), organised into {@value #PAGE_COUNT} independent pages.
 *
 * <p>
 * Derived from Avaritiaddons' Infinity Chest implementation
 * (InventoryAvaritiaddonsChest / TileEntityInfinityChest, MPL-2.0, WanionCane).
 */
public final class InventoryInfiniteBackpack implements IInventory {

    public static final int PAGE_COUNT = ItemInfiniteBackpack.PAGE_COUNT;

    private final ItemStack containerStack;
    private int pageIndex;
    public final ItemStack[] contents = new ItemStack[ItemInfiniteBackpack.STORAGE_SLOTS];

    public InventoryInfiniteBackpack(final ItemStack containerStack) {
        this(containerStack, ItemInfiniteBackpack.getCurrentPage(containerStack));
    }

    public InventoryInfiniteBackpack(final ItemStack containerStack, final int pageIndex) {
        this.containerStack = containerStack;
        this.pageIndex = pageIndex;
        loadFromNBT();
    }

    public ItemStack getContainerStack() {
        return containerStack;
    }

    public int getPageIndex() {
        return pageIndex;
    }

    /** Client-side: adopt the server-confirmed page (slot contents were already synced). */
    public void syncPage(final int page) {
        if (page >= 0 && page < PAGE_COUNT) pageIndex = page;
    }

    /**
     * Save the current page, then load the given page into the same backing array
     * (container slot references stay valid). Also persists the remembered page.
     */
    public void switchPage(final int newPage) {
        if (newPage < 0 || newPage >= PAGE_COUNT || newPage == pageIndex) return;
        saveToNBT();
        Arrays.fill(contents, null);
        pageIndex = newPage;
        loadFromNBT();
        ItemInfiniteBackpack.setCurrentPage(containerStack, newPage);
    }

    // ------------------------------------------------------------------ IInventory

    @Override
    public int getSizeInventory() {
        return ItemInfiniteBackpack.STORAGE_SLOTS;
    }

    @Override
    public ItemStack getStackInSlot(final int slot) {
        return slot >= 0 && slot < contents.length ? contents[slot] : null;
    }

    @Override
    public ItemStack decrStackSize(final int slot, final int howMany) {
        final ItemStack slotStack = contents[slot];
        if (slotStack != null) {
            final int quantity = MathHelper
                .clamp_int(MathHelper.clamp_int(howMany, 1, slotStack.getMaxStackSize()), 1, slotStack.stackSize);
            final ItemStack newStack = slotStack.copy();
            newStack.stackSize = quantity;
            if ((slotStack.stackSize -= quantity) == 0) contents[slot] = null;
            markDirty();
            return newStack;
        }
        return null;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(final int slot) {
        return null;
    }

    /**
     * Merge-aware setter, ported from TileEntityInfinityChest: same-type stacks merge into the
     * slot (up to Integer.MAX_VALUE), inserting into a matching occupied slot is redirected.
     */
    @Override
    public void setInventorySlotContents(final int slot, final ItemStack itemStack) {
        if (itemStack == null) {
            contents[slot] = null;
            markDirty();
            return;
        }
        final int perfectSlot = findSlotFor(itemStack);
        if (itemStack.stackSize < 0) return;
        final ItemStack slotStack = contents[slot];
        if (isSameItem(slotStack, itemStack)) {
            int tmp = itemStack.stackSize;
            itemStack.stackSize = slotStack.stackSize;
            slotStack.stackSize = tmp;
            markDirty();
            return;
        }
        if (perfectSlot != -1 && perfectSlot != slot) {
            if (contents[perfectSlot] != null) {
                contents[perfectSlot].stackSize += itemStack.stackSize;
                itemStack.stackSize = 0;
            } else contents[perfectSlot] = itemStack;
        } else if (perfectSlot != -1) {
            int tmp = itemStack.stackSize;
            itemStack.stackSize = slotStack.stackSize;
            slotStack.stackSize = tmp;
            if (slotStack.stackSize <= 0) contents[slot] = null;
        } else {
            contents[slot] = itemStack;
        }
        markDirty();
    }

    private boolean isSameItem(final ItemStack slotStack, final ItemStack itemStack) {
        return slotStack != null && itemStack != null
            && slotStack.getItem() == itemStack.getItem()
            && itemStack.getItemDamage() == slotStack.getItemDamage()
            && ItemStack.areItemStackTagsEqual(itemStack, slotStack);
    }

    private int findSlotFor(@Nonnull final ItemStack itemStack) {
        for (int i = 0; i < contents.length; i++) {
            final ItemStack slotStack = contents[i];
            if (isSameItem(slotStack, itemStack)) {
                if ((long) slotStack.stackSize + (long) itemStack.stackSize <= (long) Integer.MAX_VALUE) return i;
            }
        }
        return -1;
    }

    @Override
    public String getInventoryName() {
        return "container.infiniteBackpack";
    }

    @Override
    public boolean hasCustomInventoryName() {
        return false;
    }

    @Override
    public int getInventoryStackLimit() {
        return Integer.MAX_VALUE;
    }

    @Override
    public void markDirty() {
        saveToNBT();
    }

    @Override
    public boolean isUseableByPlayer(final EntityPlayer entityPlayer) {
        return true;
    }

    @Override
    public void openInventory() {
        loadFromNBT();
    }

    @Override
    public void closeInventory() {
        saveToNBT();
    }

    @Override
    public boolean isItemValidForSlot(final int slot, final ItemStack itemStack) {
        return !isForbidden(itemStack);
    }

    /** Prevent recursive storage of backpacks. */
    private static boolean isForbidden(final ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemInfiniteBackpack;
    }

    // ------------------------------------------------------------------ NBT

    /** The page compound: pages."<pageIndex>", created on demand. */
    private NBTTagCompound getPageTag(final NBTTagCompound root) {
        if (!root.hasKey(ItemInfiniteBackpack.TAG_PAGES, NBT.TAG_COMPOUND)) {
            root.setTag(ItemInfiniteBackpack.TAG_PAGES, new NBTTagCompound());
        }
        final NBTTagCompound pages = root.getCompoundTag(ItemInfiniteBackpack.TAG_PAGES);
        final String key = Integer.toString(pageIndex);
        if (!pages.hasKey(key, NBT.TAG_COMPOUND)) {
            pages.setTag(key, new NBTTagCompound());
        }
        return pages.getCompoundTag(key);
    }

    public void loadFromNBT() {
        if (containerStack == null || !containerStack.hasTagCompound()) return;
        final NBTTagCompound root = containerStack.getTagCompound()
            .getCompoundTag(ItemInfiniteBackpack.TAG_ROOT);
        migrateLegacyFormat(root);
        if (!root.hasKey(ItemInfiniteBackpack.TAG_PAGES, NBT.TAG_COMPOUND)) return;
        final NBTTagCompound pageTag = getPageTag(root);
        if (!pageTag.hasKey(ItemInfiniteBackpack.TAG_INVENTORY, NBT.TAG_LIST)) return;
        final NBTTagList nbtTagList = pageTag.getTagList(ItemInfiniteBackpack.TAG_INVENTORY, NBT.TAG_COMPOUND);
        for (int i = 0; i < nbtTagList.tagCount(); i++) {
            final NBTTagCompound slotCompound = nbtTagList.getCompoundTagAt(i);
            final int slot = slotCompound.getShort(ItemInfiniteBackpack.TAG_SLOT);
            if (slot >= 0 && slot < contents.length) {
                contents[slot] = readItemStackFromNbt(slotCompound);
            }
        }
    }

    /**
     * Pre-pages backpacks stored their inventory directly in the root tag; move it into
     * page 0 so old saves keep their items.
     */
    private void migrateLegacyFormat(final NBTTagCompound root) {
        if (root.hasKey(ItemInfiniteBackpack.TAG_INVENTORY, NBT.TAG_LIST)
            && !root.hasKey(ItemInfiniteBackpack.TAG_PAGES, NBT.TAG_COMPOUND)) {
            final NBTTagList legacy = root.getTagList(ItemInfiniteBackpack.TAG_INVENTORY, NBT.TAG_COMPOUND);
            root.setTag(ItemInfiniteBackpack.TAG_PAGES, new NBTTagCompound());
            root.getCompoundTag(ItemInfiniteBackpack.TAG_PAGES)
                .setTag(Integer.toString(0), legacy);
            root.removeTag(ItemInfiniteBackpack.TAG_INVENTORY);
        }
    }

    public void saveToNBT() {
        if (containerStack == null) return;
        final NBTTagCompound root = ItemInfiniteBackpack.getRootTag(containerStack);
        final NBTTagCompound pageTag = getPageTag(root);
        final NBTTagList nbtTagList = new NBTTagList();
        boolean hasItems = false;
        for (int i = 0; i < contents.length; i++) {
            final ItemStack itemStack = contents[i];
            if (itemStack != null) {
                final NBTTagCompound slotCompound = new NBTTagCompound();
                slotCompound.setShort(ItemInfiniteBackpack.TAG_SLOT, (short) i);
                nbtTagList.appendTag(writeItemStackToNbt(slotCompound, itemStack));
                hasItems = true;
            }
        }
        if (hasItems) pageTag.setTag(ItemInfiniteBackpack.TAG_INVENTORY, nbtTagList);
        else pageTag.removeTag(ItemInfiniteBackpack.TAG_INVENTORY);
    }

    private static ItemStack readItemStackFromNbt(final NBTTagCompound nbtTagCompound) {
        final ItemStack itemStack = ItemStack.loadItemStackFromNBT(nbtTagCompound);
        if (itemStack == null) {
            // Item may have been removed from the game (mod removed/updated) - drop it gracefully.
            return null;
        }
        itemStack.stackSize = nbtTagCompound.getInteger("intCount");
        if (nbtTagCompound.hasKey("tag")) itemStack.setTagCompound(nbtTagCompound.getCompoundTag("tag"));
        return itemStack;
    }

    private static NBTTagCompound writeItemStackToNbt(@Nonnull final NBTTagCompound nbtTagCompound,
        @Nonnull final ItemStack itemStack) {
        itemStack.writeToNBT(nbtTagCompound);
        nbtTagCompound.setInteger("intCount", itemStack.stackSize);
        if (itemStack.stackTagCompound != null) nbtTagCompound.setTag("tag", itemStack.stackTagCompound);
        return nbtTagCompound;
    }
}
