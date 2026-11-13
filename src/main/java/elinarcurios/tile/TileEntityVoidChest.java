package elinarcurios.tile;

import net.minecraft.item.ItemStack;

/**
 * 虚界百宝箱(Void Treasure Chest)方块实体:存储与永恒箱子完全一致
 * (12 页 × 243 格整体存储,每格 Integer.MAX_VALUE),多一个<b>固化</b>特性:
 * 点击 GUI 的「固化」按钮后,箱内所有非空槽位的物品数量统一刷新为
 * {@link #SOLIDIFY_COUNT}(10 亿)——少于 10 亿的补齐、多于 10 亿的压回。
 *
 * <p>
 * NBT 根键使用独立的 {@code VoidChestData}(覆写 {@link #getTagRoot()}),
 * 与永恒箱子的 {@code EternalChestData} 互不串扰。
 */
public class TileEntityVoidChest extends TileEntityEternalChest {

    /** 固化目标数量:10 亿(≤ int 上限,网络协议安全)。 */
    public static final int SOLIDIFY_COUNT = 1_000_000_000;

    /** 解离目标数量:1。 */
    public static final int DISSOCIATE_COUNT = 1;

    @Override
    protected String getTagRoot() {
        return "VoidChestData";
    }

    @Override
    public String getInventoryName() {
        return "container.elinarVoidChest";
    }

    /**
     * 固化:遍历全部 2916 格,将每个非空槽位的堆叠数统一刷新为 10 亿。
     * 空槽位保持为空;不改变物品类型与 NBT。
     */
    public void solidify() {
        setAllCounts(SOLIDIFY_COUNT);
    }

    /**
     * 解离:固化镜像操作,遍历全部 2916 格,将每个非空槽位的堆叠数统一刷新为 1。
     * 空槽位保持为空;不改变物品类型与 NBT。
     */
    public void dissociate() {
        setAllCounts(DISSOCIATE_COUNT);
    }

    private void setAllCounts(final int count) {
        boolean changed = false;
        for (int s = 0; s < TOTAL_SLOTS; s++) {
            final ItemStack stack = getStackInSlot(s);
            if (stack != null && stack.stackSize != count) {
                stack.stackSize = count;
                changed = true;
            }
        }
        if (changed) markDirty();
    }
}
