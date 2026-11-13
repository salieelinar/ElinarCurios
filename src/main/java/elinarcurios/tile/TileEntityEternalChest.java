package elinarcurios.tile;

import javax.annotation.Nonnull;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MathHelper;
import net.minecraftforge.common.util.Constants.NBT;

import elinarcurios.item.ItemInfiniteBackpack;

/**
 * 永恒箱子(Eternal Chest)方块实体:12 页 × 243 格/页,每格上限
 * {@link Integer#MAX_VALUE}。
 *
 * <p>
 * <b>整体存储模型</b>:12 页在逻辑上是<b>同一个箱子</b>——内存持有全量
 * 12×243 槽数组,槽号为全局槽号(global = page*243 + slotInPage);
 * 页只是 GUI 的<b>视图</b>。因此:
 * <ul>
 * <li>放入物品时合并搜索遍历全部 2916 格(第 10 页丢木炭会堆到第 1 页的木炭堆);</li>
 * <li>漏斗/自动化走 IInventory 全局槽号,同样全局合并,与"当前打开哪一页"无关。</li>
 * </ul>
 * 存储 NBT 仍为 pages."&lt;pageIndex&gt;".inventory(与背包同构,旧档兼容)。
 * 逻辑源自 Avaritiaddons 的 TileEntityInfinityChest(MPL-2.0, WanionCane)。
 */
public class TileEntityEternalChest extends TileEntity implements IInventory {

    public static final int PAGE_COUNT = 12;
    public static final int STORAGE_SLOTS = 243;
    public static final int TOTAL_SLOTS = PAGE_COUNT * STORAGE_SLOTS;

    /** TE 存储 NBT 的根键(写进 tile entity 的 NBT)。子类(如虚界百宝箱)覆写以使用独立根键。 */
    protected String getTagRoot() {
        return "EternalChestData";
    }

    private static final String TAG_ITEMS = "Items";
    private static final String TAG_SLOT = "Slot";

    /** 全量存储(单一箱子逻辑):pageCache[page][slotInPage]。 */
    private final ItemStack[][] pageCache = new ItemStack[PAGE_COUNT][STORAGE_SLOTS];
    private int pageIndex;

    // ------------------------------------------------------------ page view

    public int getPageIndex() {
        return pageIndex;
    }

    /** Client-side: adopt the server-confirmed page (slot contents already synced). */
    public void syncPage(final int page) {
        if (page >= 0 && page < PAGE_COUNT) pageIndex = page;
    }

    /** Switch the visible page. Data stays in the shared array; only the view changes. */
    public void switchPage(final int newPage) {
        if (newPage < 0 || newPage >= PAGE_COUNT || newPage == pageIndex) return;
        pageIndex = newPage;
        markDirty();
    }

    // ------------------------------------------------------------ IInventory (global slot ids)

    @Override
    public int getSizeInventory() {
        return TOTAL_SLOTS;
    }

    @Override
    public ItemStack getStackInSlot(final int slot) {
        if (slot < 0 || slot >= TOTAL_SLOTS) return null;
        return pageCache[slot / STORAGE_SLOTS][slot % STORAGE_SLOTS];
    }

    @Override
    public ItemStack decrStackSize(final int slot, final int howMany) {
        final ItemStack slotStack = getStackInSlot(slot);
        if (slotStack != null) {
            final int quantity = MathHelper
                .clamp_int(MathHelper.clamp_int(howMany, 1, slotStack.getMaxStackSize()), 1, slotStack.stackSize);
            final ItemStack newStack = slotStack.copy();
            newStack.stackSize = quantity;
            if ((slotStack.stackSize -= quantity) == 0) pageCache[slot / STORAGE_SLOTS][slot % STORAGE_SLOTS] = null;
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
     * Merge-aware setter with a <b>whole-chest</b> merge search: same-type stacks anywhere
     * in the 12 pages merge (up to Integer.MAX_VALUE), so drops/hopper inserts land in the
     * existing stack regardless of which page is currently open.
     */
    @Override
    public void setInventorySlotContents(final int slot, final ItemStack itemStack) {
        if (itemStack == null) {
            pageCache[slot / STORAGE_SLOTS][slot % STORAGE_SLOTS] = null;
            markDirty();
            return;
        }
        if (itemStack.stackSize < 0) return;
        final int perfectSlot = findSlotFor(itemStack);
        final ItemStack slotStack = getStackInSlot(slot);
        if (isSameItem(slotStack, itemStack)) {
            int tmp = itemStack.stackSize;
            itemStack.stackSize = slotStack.stackSize;
            slotStack.stackSize = tmp;
            markDirty();
            return;
        }
        if (perfectSlot != -1 && perfectSlot != slot) {
            final ItemStack perfectStack = getStackInSlot(perfectSlot);
            if (perfectStack != null) {
                perfectStack.stackSize += itemStack.stackSize;
                itemStack.stackSize = 0;
            } else pageCache[perfectSlot / STORAGE_SLOTS][perfectSlot % STORAGE_SLOTS] = itemStack;
        } else if (perfectSlot != -1) {
            int tmp = itemStack.stackSize;
            itemStack.stackSize = slotStack.stackSize;
            slotStack.stackSize = tmp;
            if (slotStack.stackSize <= 0) pageCache[slot / STORAGE_SLOTS][slot % STORAGE_SLOTS] = null;
        } else {
            pageCache[slot / STORAGE_SLOTS][slot % STORAGE_SLOTS] = itemStack;
        }
        markDirty();
    }

    private boolean isSameItem(final ItemStack slotStack, final ItemStack itemStack) {
        return slotStack != null && itemStack != null
            && slotStack.getItem() == itemStack.getItem()
            && itemStack.getItemDamage() == slotStack.getItemDamage()
            && ItemStack.areItemStackTagsEqual(itemStack, slotStack);
    }

    /** Whole-chest search: the first mergeable stack across all 12 pages. */
    private int findSlotFor(@Nonnull final ItemStack itemStack) {
        for (int i = 0; i < TOTAL_SLOTS; i++) {
            final ItemStack slotStack = getStackInSlot(i);
            if (isSameItem(slotStack, itemStack)) {
                if ((long) slotStack.stackSize + (long) itemStack.stackSize <= (long) Integer.MAX_VALUE) return i;
            }
        }
        return -1;
    }

    @Override
    public String getInventoryName() {
        return "container.eternalChest";
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
        if (worldObj != null) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }

    @Override
    public boolean isUseableByPlayer(final EntityPlayer entityPlayer) {
        return worldObj.getTileEntity(xCoord, yCoord, zCoord) == this
            && entityPlayer.getDistanceSq(xCoord + 0.5D, yCoord + 0.5D, zCoord + 0.5D) <= 64.0D;
    }

    @Override
    public void openInventory() {
        // Whole-chest storage lives in memory; nothing to (re)load per page.
    }

    @Override
    public void closeInventory() {
        // Data is kept in the shared array and persisted via writeToNBT.
    }

    @Override
    public boolean isItemValidForSlot(final int slot, final ItemStack itemStack) {
        return itemStack == null || !(itemStack.getItem() instanceof ItemInfiniteBackpack);
    }

    // ------------------------------------------------------------ TileEntity / NBT

    @Override
    public void readFromNBT(final NBTTagCompound compound) {
        super.readFromNBT(compound);
        readCustomNBT(compound);
    }

    @Override
    public void writeToNBT(final NBTTagCompound compound) {
        super.writeToNBT(compound);
        writeCustomNBT(compound);
    }

    /** Restore storage from an external compound (world save or carried ItemStack). */
    public void readCustomNBT(final NBTTagCompound compound) {
        clear();
        final NBTTagList list = compound.getCompoundTag(getTagRoot())
            .getTagList(TAG_ITEMS, NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            final NBTTagCompound slotCompound = list.getCompoundTagAt(i);
            final int slot = slotCompound.getShort(TAG_SLOT);
            if (slot >= 0 && slot < TOTAL_SLOTS) {
                pageCache[slot / STORAGE_SLOTS][slot % STORAGE_SLOTS] = readItemStackFromNbt(slotCompound);
            }
        }
    }

    /** Serialise the whole storage (a single 2916-slot list) into an external compound. */
    public void writeCustomNBT(final NBTTagCompound compound) {
        final NBTTagList list = new NBTTagList();
        for (int s = 0; s < TOTAL_SLOTS; s++) {
            final ItemStack itemStack = pageCache[s / STORAGE_SLOTS][s % STORAGE_SLOTS];
            if (itemStack != null) {
                final NBTTagCompound slotCompound = new NBTTagCompound();
                slotCompound.setShort(TAG_SLOT, (short) s);
                list.appendTag(writeItemStackToNbt(slotCompound, itemStack));
            }
        }
        final NBTTagCompound root = new NBTTagCompound();
        root.setTag(TAG_ITEMS, list);
        compound.setTag(getTagRoot(), root);
    }

    private void clear() {
        for (int p = 0; p < PAGE_COUNT; p++) {
            for (int s = 0; s < STORAGE_SLOTS; s++) {
                pageCache[p][s] = null;
            }
        }
    }

    /**
     * Directly assign a slot (no whole-chest merge). Used by sync pushes: the slot cache
     * must mirror exactly what the server sent — merge logic would zero/redirect the
     * displayed stack and desync the cache from the server (item "disappearing" / duping).
     */
    public void rawSetStack(final int slot, final ItemStack itemStack) {
        if (slot >= 0 && slot < TOTAL_SLOTS) {
            pageCache[slot / STORAGE_SLOTS][slot % STORAGE_SLOTS] = itemStack;
        }
    }

    private static ItemStack readItemStackFromNbt(final NBTTagCompound nbtTagCompound) {
        final ItemStack itemStack = ItemStack.loadItemStackFromNBT(nbtTagCompound);
        if (itemStack == null) return null; // item removed from the game - drop gracefully
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
