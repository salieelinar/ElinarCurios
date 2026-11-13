package elinarcurios.util;

import java.lang.reflect.Field;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import elinarcurios.item.ItemLunchBox;

/**
 * 抑制「吃一口发一整个午餐盒 NBT」的原版同步。
 *
 * <p>
 * 1.7.10 里服务端每 tick 跑 {@code player.inventoryContainer.detectAndSendChanges()}，
 * 它把每个槽的当前值与上一 tick 的快照（{@code Container.inventoryItemStacks}）比对，
 * 一旦发现不同就整包重发该物品（含全部 NBT）。午餐盒是物品 NBT 存储的多页仓库，
 * 吃一口就改一次 NBT，于是每口都会把整个仓库推给客户端。
 *
 * <p>
 * 做法：进食后立刻把快照对齐成新值，下一个 tick 原版便认为「没有变化」而不再发整包；
 * 变化的那个槽由 {@code LunchBoxEatSync} 增量包推给客户端。
 *
 * <p>
 * 字段访问用反射（{@code Container.inventoryItemStacks} 是 protected，且运行时是 srg 名）：
 * 先按 MCP/srg 两个已知名字找，找不到再按「元素不是 Slot 的 List 字段」兜底；
 * 全部失败就静默跳过 —— 退化成允许整包同步，功能不受影响。
 */
public final class VanillaSyncGuard {

    private static boolean resolved;
    private static Field snapshotField;

    private VanillaSyncGuard() {}

    /** 把玩家背包里所有午餐盒槽位的原版同步快照对齐为当前值。 */
    @SuppressWarnings("unchecked")
    public static void alignLunchBoxSnapshots(final EntityPlayerMP player) {
        if (player == null) return;
        final Field field = snapshotField();
        if (field == null) return;
        try {
            final Container container = player.inventoryContainer;
            if (container == null) return;
            final Object raw = field.get(container);
            if (!(raw instanceof List)) return;
            final List<Object> snapshots = (List<Object>) raw;
            for (int i = 0; i < container.inventorySlots.size() && i < snapshots.size(); i++) {
                final ItemStack stack = ((Slot) container.inventorySlots.get(i)).getStack();
                if (ItemLunchBox.isLunchBox(stack)) snapshots.set(i, stack.copy());
            }
        } catch (final Throwable ignored) {
            // 反射失败不影响玩法，只是会退化成原版整包同步。
        }
    }

    private static Field snapshotField() {
        if (resolved) return snapshotField;
        resolved = true;
        for (final String name : new String[] { "inventoryItemStacks", "field_75153_a" }) {
            try {
                final Field candidate = Container.class.getDeclaredField(name);
                candidate.setAccessible(true);
                snapshotField = candidate;
                return snapshotField;
            } catch (final Throwable ignored) {
                // 换下一个候选名。
            }
        }
        try {
            final Container probe = new Container() {

                @Override
                public boolean canInteractWith(final net.minecraft.entity.player.EntityPlayer player) {
                    return false;
                }
            };
            for (final Field candidate : Container.class.getDeclaredFields()) {
                if (!List.class.isAssignableFrom(candidate.getType())) continue;
                candidate.setAccessible(true);
                final Object value = candidate.get(probe);
                if (!(value instanceof List)) continue;
                boolean holdsSlots = false;
                for (final Object element : (List<?>) value) {
                    if (element instanceof Slot) {
                        holdsSlots = true;
                        break;
                    }
                }
                if (!holdsSlots) {
                    snapshotField = candidate;
                    break;
                }
            }
        } catch (final Throwable ignored) {
            snapshotField = null;
        }
        return snapshotField;
    }
}
