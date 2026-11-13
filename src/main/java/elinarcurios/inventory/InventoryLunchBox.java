package elinarcurios.inventory;

import java.util.Arrays;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;
import elinarcurios.item.ItemLunchBox;
import elinarcurios.util.LunchBoxNbt;
import squeek.spiceoflife.inventory.FoodContainerInventory;

/**
 * 午餐盒的存储核心：{@value ItemLunchBox#PAGE_COUNT} 页 × {@value ItemLunchBox#SLOTS_PER_PAGE} 格，
 * 每格最多 {@link Integer#MAX_VALUE} 个物品。
 *
 * <p>
 * 与无尽背包「单页视图 + 换页落盘」不同，这里把 2916 个槽位**一次性全读进内存**（换页只是改一个页号），
 * 因为 SpiceOfLife 的进食逻辑（{@code MealPrioritizationHelper}）接收的正是一个 {@code IInventory} ——
 * 让 {@code getSizeInventory()} 直接返回 2916，就能一次性得到「跨全页自动选最优食物」，
 * 无需为多页单独写一套选食算法。
 *
 * <p>
 * 序列化不使用 SpiceOfLife 自带的 {@code Inventory/Items} 结构：那里的 Count 是 byte，
 * 超过 127 的堆叠存档即损坏。这里覆写为 {@link LunchBoxNbt} 的多页 + {@code intCount} 方案。
 */
public class InventoryLunchBox extends FoodContainerInventory {

    private int pageIndex;

    public InventoryLunchBox(final ItemLunchBox item, final ItemStack stack) {
        super(item, stack);
    }

    public int getPageIndex() {
        return pageIndex;
    }

    /** 客户端：采纳服务端确认的页号（数据本就全在内存，无需重载）。 */
    public void syncPage(final int page) {
        if (page >= 0 && page < ItemLunchBox.PAGE_COUNT) pageIndex = page;
    }

    /** 换页：只改页号并记住，槽位数组保持不变（也因此不需要重发全量快照）。 */
    public void switchPage(final int newPage) {
        if (newPage < 0 || newPage >= ItemLunchBox.PAGE_COUNT || newPage == pageIndex) return;
        pageIndex = newPage;
        ItemLunchBox.setCurrentPage(itemStackFoodContainer, newPage);
    }

    /** 页内槽 -> 扁平槽。 */
    public int flatSlot(final int slotInPage) {
        return pageIndex * ItemLunchBox.SLOTS_PER_PAGE + slotInPage;
    }

    /** 当前页在某扁平槽上吗（增量同步用于过滤本页显示）。 */
    public boolean isOnCurrentPage(final int flatSlot) {
        return flatSlot >= 0 && flatSlot / ItemLunchBox.SLOTS_PER_PAGE == pageIndex;
    }

    /**
     * 把视图重新指向另一个物品实例并重读数据。
     *
     * <p>
     * 客户端的物品栏会被服务端的整包同步替换成**新的 ItemStack 实例**，而打开界面时抓住的那份
     * 随即成为孤立对象 —— 继续往它身上写，客户端的盒盖状态与食物数量就会与物品栏里的实际物品脱节。
     */
    public void retarget(final ItemStack stack) {
        if (stack == null || stack == itemStackFoodContainer) return;
        itemStackFoodContainer = stack;
        readFromNBTData(null);
    }

    // ------------------------------------------------------------------ 序列化

    /**
     * SpiceOfLife 传入的是它自己 {@code Inventory} 子标签，这里刻意忽略，改读多页结构。
     * 注意本方法由父类构造器调用，此时只有父类字段已赋值 —— 只用 {@code itemStackFoodContainer}。
     */
    @Override
    public void readFromNBTData(final NBTTagCompound ignored) {
        Arrays.fill(inventoryItems, null);
        pageIndex = ItemLunchBox.getCurrentPage(itemStackFoodContainer);
        LunchBoxNbt.readInto(itemStackFoodContainer, inventoryItems);
    }

    @Override
    public void writeToNBTData(final NBTTagCompound ignored) {
        LunchBoxNbt.writeFrom(itemStackFoodContainer, inventoryItems);
    }

    @Override
    public void onInventoryChanged() {
        // 客户端手里的物品是服务端同步下来的另一个实例，写回前先找回当前这个（照抄父类语义）。
        if (FMLCommonHandler.instance()
            .getEffectiveSide() == Side.CLIENT) {
            findMatchingClientItemStack();
        }
        writeToNBTData(null);
    }
}
