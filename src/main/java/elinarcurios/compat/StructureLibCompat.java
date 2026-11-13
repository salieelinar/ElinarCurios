package elinarcurios.compat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import com.gtnewhorizon.structurelib.util.InventoryUtility;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import elinarcurios.inventory.InventoryInfiniteBackpack;
import elinarcurios.item.ItemInfiniteBackpack;
import elinarcurios.util.BagUtils;

/**
 * StructureLib「多方块机器全息投影仪」的取料兼容。
 *
 * <p>
 * 投影仪在生存模式下自动搭建时，通过 {@link InventoryUtility} 的注册表寻找方块来源：
 * 先遍历玩家物品栏等 provider，再对物品栏里的每件物品跑 stack extractor。无尽背包有两种
 * 存在形态，这里用同一个 provider 全部覆盖：
 * <ul>
 * <li>穿在身上：物品在 {@code BagProperty} 里，不在任何 IInventory 中；</li>
 * <li>放在物品栏：物品本体在，但内容在 ItemStack 的 NBT 里。</li>
 * </ul>
 * 不额外注册 stack extractor：这样 StructureLib 的递归提取链路完全不会为我们展开，
 * 行为更可预测，也不会出现「背包里套背包」的递归。
 *
 * <p>
 * 存储格式零改动：不新增任何 NBT 字段，读写完全复用
 * {@link InventoryInfiniteBackpack} 的分页格式（与打开背包 GUI 时的保存路径同一条）。
 * 只有真正扣掉方块时才写回，并且只保存内容发生变化的页；取料来源按服务器 tick 缓存，
 * 每 tick 最多解析一次 10 页 NBT。
 */
public final class StructureLibCompat {

    /** 排在玩家物品栏/饰品/冒险背包/末影箱之后（StructureLib 会在加载完成时自动补进 cfg 的 ordering）。 */
    private static final String PROVIDER_KEY = "6500-infinite-backpack";

    private static final Map<UUID, CachedSource> CACHE = new HashMap<>();

    private StructureLibCompat() {}

    public static void register() {
        InventoryUtility.registerInventoryProvider(PROVIDER_KEY, new SourceProvider());
        FMLCommonHandler.instance()
            .bus()
            .register(new CacheCleaner());
    }

    /**
     * 注意：FML 会给 {@code @SubscribeEvent} 生成一个 ASMEventHandler 子类（位于
     * {@code cpw.mods.fml.common.eventhandler}，由独立的 ASMClassLoader 加载）。JVM 的访问检查按
     * 运行时包算，跨 ClassLoader 即跨包 ⇒ 监听器类必须是 <b>public</b>，否则事件第一次真正触发时抛
     * {@code IllegalAccessError}（本类 v1.2.24~v1.2.34 曾写成 private，单机「保存并退出」不走登出
     * 事件所以一直没暴露，掉线时才崩）。
     */
    public static final class CacheCleaner {

        @SubscribeEvent
        public void onPlayerLoggedOut(final PlayerEvent.PlayerLoggedOutEvent event) {
            CACHE.remove(event.player.getUniqueID());
        }
    }

    private static final class CachedSource {

        final int tick;
        final InfiniteBackpackSource source;

        CachedSource(final int tick, final InfiniteBackpackSource source) {
            this.tick = tick;
            this.source = source;
        }
    }

    private static final class SourceProvider implements InventoryUtility.InventoryProvider<InfiniteBackpackSource> {

        @Override
        public InfiniteBackpackSource getInventory(final EntityPlayerMP player) {
            final UUID uuid = player.getUniqueID();
            final CachedSource cached = CACHE.get(uuid);
            if (cached != null && cached.tick == player.ticksExisted && cached.source.matches(player)) {
                return cached.source;
            }
            final InfiniteBackpackSource source = InfiniteBackpackSource.build(player);
            if (source == null) {
                CACHE.remove(uuid);
                return null;
            }
            CACHE.put(uuid, new CachedSource(player.ticksExisted, source));
            return source;
        }

        @Override
        public void markDirty(final InfiniteBackpackSource source) {
            source.flush();
        }
    }

    /**
     * 一件或多件无尽背包的全页（10 页 × 243 格）取料来源。Iterator 直接走各页的 contents
     * 数组，remove() 只把格位清空，真正的 NBT 写回统一发生在 {@link #flush()}。
     */
    private static final class InfiniteBackpackSource implements Iterable<ItemStack> {

        private final EntityPlayerMP player;
        private final ItemStack wornBag;
        private final List<ItemStack> carriedBags;
        private final List<InventoryInfiniteBackpack> pages;
        private final boolean[] pageIsWorn;
        private final int[][] snapshot;
        private int lastSyncTick = Integer.MIN_VALUE;

        private InfiniteBackpackSource(final EntityPlayerMP player, final ItemStack wornBag,
            final List<ItemStack> carriedBags) {
            this.player = player;
            this.wornBag = wornBag;
            this.carriedBags = carriedBags;

            final List<ItemStack> bags = new ArrayList<>(1 + carriedBags.size());
            if (wornBag != null) bags.add(wornBag);
            bags.addAll(carriedBags);

            pages = new ArrayList<>(bags.size() * ItemInfiniteBackpack.PAGE_COUNT);
            pageIsWorn = new boolean[bags.size() * ItemInfiniteBackpack.PAGE_COUNT];
            for (int bag = 0; bag < bags.size(); bag++) {
                for (int page = 0; page < ItemInfiniteBackpack.PAGE_COUNT; page++) {
                    pages.add(new InventoryInfiniteBackpack(bags.get(bag), page));
                    pageIsWorn[pages.size() - 1] = bag == 0 && wornBag != null;
                }
            }

            snapshot = new int[pages.size()][];
            for (int i = 0; i < pages.size(); i++) {
                snapshot[i] = snapshotOf(pages.get(i));
            }
        }

        static InfiniteBackpackSource build(final EntityPlayerMP player) {
            final ItemStack worn = BagUtils.getWornInfiniteBackpack(player);
            final List<ItemStack> carried = new ArrayList<>();
            for (final ItemStack stack : player.inventory.mainInventory) {
                if (ItemInfiniteBackpack.isInfiniteBackpack(stack) && stack != worn) carried.add(stack);
            }
            return worn == null && carried.isEmpty() ? null : new InfiniteBackpackSource(player, worn, carried);
        }

        /** 缓存命中校验：背包仍然是同一件、位置没变。 */
        boolean matches(final EntityPlayerMP player) {
            if (player != this.player) return false;
            if (BagUtils.getWornInfiniteBackpack(player) != wornBag) return false;
            int index = 0;
            for (final ItemStack stack : player.inventory.mainInventory) {
                if (!ItemInfiniteBackpack.isInfiniteBackpack(stack) || stack == wornBag) continue;
                if (index >= carriedBags.size() || carriedBags.get(index) != stack) return false;
                index++;
            }
            return index == carriedBags.size();
        }

        @Override
        public Iterator<ItemStack> iterator() {
            return new Iterator<ItemStack>() {

                private int page = 0;
                private int slot = 0;
                private int lastPage = -1;
                private int lastSlot = -1;

                @Override
                public boolean hasNext() {
                    while (page < pages.size()) {
                        if (slot < ItemInfiniteBackpack.STORAGE_SLOTS) return true;
                        page++;
                        slot = 0;
                    }
                    return false;
                }

                @Override
                public ItemStack next() {
                    if (!hasNext()) throw new NoSuchElementException();
                    lastPage = page;
                    lastSlot = slot;
                    return pages.get(page).contents[slot++];
                }

                @Override
                public void remove() {
                    if (lastPage < 0) throw new IllegalStateException();
                    pages.get(lastPage).contents[lastSlot] = null;
                    lastPage = -1;
                    lastSlot = -1;
                }
            };
        }

        /** 只写回内容变化的页；顺带同步客户端（穿戴的那件发整包，携带的走物品栏容器同步）。 */
        void flush() {
            boolean wornChanged = false;
            boolean carriedChanged = false;
            for (int i = 0; i < pages.size(); i++) {
                final InventoryInfiniteBackpack page = pages.get(i);
                final int[] now = snapshotOf(page);
                if (Arrays.equals(now, snapshot[i])) continue;
                page.saveToNBT();
                snapshot[i] = now;
                if (pageIsWorn[i]) wornChanged = true;
                else carriedChanged = true;
            }
            if (wornChanged || carriedChanged) sync();
        }

        private void sync() {
            if (player.ticksExisted == lastSyncTick) return;
            lastSyncTick = player.ticksExisted;
            if (wornBag != null) BagUtils.syncWornBagToClient(player);
            player.inventoryContainer.detectAndSendChanges();
        }

        private static int[] snapshotOf(final InventoryInfiniteBackpack page) {
            final int[] sizes = new int[ItemInfiniteBackpack.STORAGE_SLOTS];
            for (int i = 0; i < sizes.length; i++) {
                final ItemStack stack = page.contents[i];
                sizes[i] = stack == null ? 0 : stack.stackSize;
            }
            return sizes;
        }
    }
}
