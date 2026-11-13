package elinarcurios.util;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants.NBT;

import elinarcurios.item.ItemLunchBox;

/**
 * 午餐盒多页存储的 NBT 读写原语。
 *
 * <p>
 * 结构（挂在午餐盒物品 tag 的 {@code lunchbox} 根键下，与无尽背包的 {@code elinarcurios} 根互不串扰）:
 *
 * <pre>
 * lunchbox.pages."&lt;page&gt;".inventory[ {Slot, id, Count(byte), Damage, intCount, tag?} ]
 * lunchbox.currentPage
 * </pre>
 *
 * <p>
 * 原版 {@link ItemStack#writeToNBT} 只写 {@code Byte("Count")}，超过 127 的堆叠存档后会损坏，
 * 因此额外补写 {@code int("intCount")} 并在读取时覆盖 stackSize（与无尽背包/永恒箱子同款做法）。
 */
public final class LunchBoxNbt {

    private LunchBoxNbt() {}

    /** 一个物品条目 -> ItemStack（intCount 优先，非法数量返回 null）。 */
    public static ItemStack readEntry(final NBTTagCompound entry) {
        if (entry == null) return null;
        final ItemStack stack = ItemStack.loadItemStackFromNBT(entry);
        if (stack == null) return null;
        final int count = entry.hasKey(ItemLunchBox.TAG_INT_COUNT) ? entry.getInteger(ItemLunchBox.TAG_INT_COUNT)
            : stack.stackSize;
        if (count <= 0) return null;
        stack.stackSize = count;
        return stack;
    }

    /** ItemStack -> 一个物品条目。 */
    public static NBTTagCompound writeEntry(final ItemStack stack, final int slotInPage) {
        final NBTTagCompound entry = new NBTTagCompound();
        entry.setShort(ItemLunchBox.TAG_SLOT, (short) slotInPage);
        stack.writeToNBT(entry);
        entry.setInteger(ItemLunchBox.TAG_INT_COUNT, stack.stackSize);
        return entry;
    }

    /**
     * 占用槽数（跨全部页），用于 tooltip 与图标判空。
     *
     * <p>
     * 只在写路径更新过的 {@code usedSlots} 计数缓存上取值（O(1)）—— 物品图标每帧都会问一次
     * 「盒子是不是空的」，逐页扫 NBT 列表太浪费。缓存缺失或越界时回退为扫描。
     */
    public static int countUsed(final ItemStack box) {
        final NBTTagCompound root = ItemLunchBox.peekRootTag(box);
        if (root == null) return 0;
        if (root.hasKey(ItemLunchBox.TAG_USED)) {
            final int cached = root.getInteger(ItemLunchBox.TAG_USED);
            if (cached >= 0 && cached <= ItemLunchBox.TOTAL_SLOTS) return cached;
        }
        return countUsedByScan(box);
    }

    private static int countUsedByScan(final ItemStack box) {
        final NBTTagCompound root = ItemLunchBox.peekRootTag(box);
        if (root == null || !root.hasKey(ItemLunchBox.TAG_PAGES, NBT.TAG_COMPOUND)) return 0;
        final NBTTagCompound pages = root.getCompoundTag(ItemLunchBox.TAG_PAGES);
        int used = 0;
        for (int page = 0; page < ItemLunchBox.PAGE_COUNT; page++) {
            final NBTTagCompound pageTag = pages.getCompoundTag(Integer.toString(page));
            used += pageTag.getTagList(ItemLunchBox.TAG_INVENTORY, NBT.TAG_COMPOUND)
                .tagCount();
        }
        return used;
    }

    /** 把物品里的全部页读进扁平数组（长度 {@link ItemLunchBox#TOTAL_SLOTS}）。 */
    public static void readInto(final ItemStack box, final ItemStack[] slots) {
        if (slots == null) return;
        final NBTTagCompound root = ItemLunchBox.peekRootTag(box);
        if (root == null || !root.hasKey(ItemLunchBox.TAG_PAGES, NBT.TAG_COMPOUND)) return;
        final NBTTagCompound pages = root.getCompoundTag(ItemLunchBox.TAG_PAGES);
        for (int page = 0; page < ItemLunchBox.PAGE_COUNT; page++) {
            final NBTTagCompound pageTag = pages.getCompoundTag(Integer.toString(page));
            final NBTTagList list = pageTag.getTagList(ItemLunchBox.TAG_INVENTORY, NBT.TAG_COMPOUND);
            final int base = page * ItemLunchBox.SLOTS_PER_PAGE;
            for (int i = 0; i < list.tagCount(); i++) {
                final NBTTagCompound entry = list.getCompoundTagAt(i);
                final int slotInPage = entry.getShort(ItemLunchBox.TAG_SLOT);
                if (slotInPage < 0 || slotInPage >= ItemLunchBox.SLOTS_PER_PAGE) continue;
                final ItemStack stack = readEntry(entry);
                if (stack != null) slots[base + slotInPage] = stack;
            }
        }
    }

    /** 把扁平数组整个写回物品（空页不落盘）。 */
    public static void writeFrom(final ItemStack box, final ItemStack[] slots) {
        if (box == null || slots == null) return;
        final NBTTagCompound root = ItemLunchBox.getRootTag(box);
        final NBTTagCompound pages = new NBTTagCompound();
        int used = 0;
        for (int page = 0; page < ItemLunchBox.PAGE_COUNT; page++) {
            final NBTTagList list = new NBTTagList();
            final int base = page * ItemLunchBox.SLOTS_PER_PAGE;
            for (int i = 0; i < ItemLunchBox.SLOTS_PER_PAGE; i++) {
                final ItemStack stack = slots[base + i];
                if (stack == null || stack.stackSize <= 0) continue;
                list.appendTag(writeEntry(stack, i));
            }
            if (list.tagCount() > 0) {
                final NBTTagCompound pageTag = new NBTTagCompound();
                pageTag.setTag(ItemLunchBox.TAG_INVENTORY, list);
                pages.setTag(Integer.toString(page), pageTag);
                used += list.tagCount();
            }
        }
        root.setTag(ItemLunchBox.TAG_PAGES, pages);
        root.setInteger(ItemLunchBox.TAG_USED, used);
        bumpVersion(root);
    }

    /** 单槽写入（增量同步用）：定位到扁平索引所在的页，只动那一条。 */
    public static void writeSlot(final ItemStack box, final int flatSlot, final ItemStack stack) {
        if (box == null || flatSlot < 0 || flatSlot >= ItemLunchBox.TOTAL_SLOTS) return;
        final int page = flatSlot / ItemLunchBox.SLOTS_PER_PAGE;
        final int slotInPage = flatSlot % ItemLunchBox.SLOTS_PER_PAGE;

        final NBTTagCompound root = ItemLunchBox.getRootTag(box);
        final NBTTagCompound pages = root.getCompoundTag(ItemLunchBox.TAG_PAGES);
        final NBTTagCompound pageTag = pages.getCompoundTag(Integer.toString(page));
        final NBTTagList old = pageTag.getTagList(ItemLunchBox.TAG_INVENTORY, NBT.TAG_COMPOUND);

        final NBTTagList rebuilt = new NBTTagList();
        for (int i = 0; i < old.tagCount(); i++) {
            final NBTTagCompound entry = old.getCompoundTagAt(i);
            if (entry.getShort(ItemLunchBox.TAG_SLOT) == slotInPage) continue;
            rebuilt.appendTag(entry);
        }
        if (stack != null && stack.stackSize > 0) rebuilt.appendTag(writeEntry(stack, slotInPage));

        if (rebuilt.tagCount() > 0) pageTag.setTag(ItemLunchBox.TAG_INVENTORY, rebuilt);
        else pageTag.removeTag(ItemLunchBox.TAG_INVENTORY);
        pages.setTag(Integer.toString(page), pageTag);
        root.setTag(ItemLunchBox.TAG_PAGES, pages);
        root.setInteger(ItemLunchBox.TAG_USED, countUsedByScan(box));
        bumpVersion(root);
    }

    /**
     * 内容版本：选食缓存的失效键。任何写路径都会让它 +1，读取是 O(1) ——
     * 不必为了判断「盒子内容变没变」去遍历 2916 个槽。
     */
    public static int contentVersion(final ItemStack box) {
        final NBTTagCompound root = ItemLunchBox.peekRootTag(box);
        return root == null ? 0 : root.getInteger(ItemLunchBox.TAG_VERSION);
    }

    private static void bumpVersion(final NBTTagCompound root) {
        root.setInteger(ItemLunchBox.TAG_VERSION, root.getInteger(ItemLunchBox.TAG_VERSION) + 1);
    }
}
