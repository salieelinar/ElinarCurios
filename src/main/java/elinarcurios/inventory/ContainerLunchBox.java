package elinarcurios.inventory;

import javax.annotation.Nonnull;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import elinarcurios.item.ItemLunchBox;

/**
 * 午餐盒仓库界面：{@value ContainerMultiPage#STORAGE_SLOT_COUNT} 个可见槽位是<b>当前页</b>的视图
 * （页内索引 0..242 映射到扁平索引 {@code page * 243 + i}），玩家背包照常共用
 * {@link ContainerMultiPage} 的大堆叠点击 / 合并 / int 安全同步。
 *
 * <p>
 * 与永恒箱子不同，午餐盒的数据源就是物品自身的 NBT，客户端也持有同一份，
 * 因此槽位直接读 {@link InventoryLunchBox}，不需要额外的本地缓存层。
 */
public final class ContainerLunchBox extends ContainerMultiPage {

    public final InventoryLunchBox inventory;

    public ContainerLunchBox(@Nonnull final ItemStack boxStack, final InventoryPlayer inventoryPlayer) {
        super(inventoryPlayer, new InventoryLunchBox((ItemLunchBox) boxStack.getItem(), boxStack));
        this.inventory = (InventoryLunchBox) storageInventory;
    }

    @Override
    protected Slot createStorageSlot(final IInventory inventory, final int index, final int x, final int y) {
        // 注意：本方法由父类构造器调用，此时子类字段尚未赋值，只能用入参。
        return new LunchBoxSlot((InventoryLunchBox) inventory, index, x, y);
    }

    @Override
    public int getCurrentPage() {
        return inventory.getPageIndex();
    }

    @Override
    public int getPageCount() {
        return ItemLunchBox.PAGE_COUNT;
    }

    /** 客户端：采纳服务端确认的页号。 */
    @Override
    public void syncPage(final int page) {
        inventory.syncPage(page);
    }

    /**
     * 换页：数据本来就全在内存里，槽位指向的页一变，243 个槽自然显示新页的内容，
     * 只需把新页推给正在看这个界面的玩家。
     */
    @Override
    public void switchPage(final int page) {
        inventory.switchPage(page);
        resyncAllToPlayers();
    }

    /**
     * 客户端 GUI 每次绘制时校正：物品栏里的午餐盒实例若已被服务端的整包同步换成新的，
     * 就把存储视图重新指过去 —— 否则客户端对盒子的读写都落在一个孤立实例上。
     */
    public void retargetClientStack(final EntityPlayer player) {
        if (player == null) return;
        final ItemStack held = player.inventory.getCurrentItem();
        if (held != null && ItemLunchBox.isLunchBox(held)) inventory.retarget(held);
    }

    /** 进食增量同步落到客户端时刷新对应槽（吃的那一格可能不在当前页，则只更新数据）。 */
    public void applyEatenSlot(final int flatSlot, final ItemStack stack) {
        if (!inventory.isOnCurrentPage(flatSlot)) {
            inventory.setInventorySlotContents(flatSlot, stack == null || stack.stackSize <= 0 ? null : stack.copy());
            return;
        }
        syncData(stack, flatSlot % ItemLunchBox.SLOTS_PER_PAGE, stack == null ? 0 : stack.stackSize);
    }

    /** 当前页的页内槽位：读写都要先算成扁平索引，Slot 自带的 slotIndex 只当页内偏移用。 */
    private static final class LunchBoxSlot extends Slot {

        private final InventoryLunchBox inventory;
        private final int slotInPage;

        private LunchBoxSlot(final InventoryLunchBox inventory, final int slotInPage, final int x, final int y) {
            super(inventory, slotInPage, x, y);
            this.inventory = inventory;
            this.slotInPage = slotInPage;
        }

        @Override
        public ItemStack getStack() {
            return inventory.getStackInSlot(inventory.flatSlot(slotInPage));
        }

        @Override
        public void putStack(final ItemStack stack) {
            inventory.setInventorySlotContents(inventory.flatSlot(slotInPage), stack);
            onSlotChanged();
        }

        @Override
        public ItemStack decrStackSize(final int amount) {
            final ItemStack result = inventory.decrStackSize(inventory.flatSlot(slotInPage), amount);
            onSlotChanged();
            return result;
        }

        @Override
        public boolean isItemValid(final ItemStack stack) {
            return inventory.isItemValidForSlot(slotInPage, stack);
        }

        @Override
        public int getSlotStackLimit() {
            return Integer.MAX_VALUE;
        }
    }
}
