package elinarcurios.machine;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import cpw.mods.fml.common.FMLLog;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * 存储器 UUID → 实例 的全局表（只在服务端有内容）。
 *
 * <p>
 * 芷若 2026-11-14 的方案：存储器带一个摆下就生成、拆了也不变的 UUID；仓绑 UUID 而不是坐标，
 * 于是<b>把存储器拆了换个地方放，仓会自动重新连上</b>，不用一个个重绑。
 *
 * <p>
 * 为什么不做"坐标 → 实例"的常驻表：坐标会变，UUID 不会。这里只在<b>已加载</b>的存储器之间建表：
 * 区块没加载 → 表里没有 → 仓这侧当作"暂时找不到"（和以前一样，不加载远处区块）。
 *
 * <p>
 * 开销：查表是一次 {@link HashMap#get}（比以前"维度比较 + World.blockExists + World.getTileEntity"
 * 便宜）；注册/注销是 O(1)，只在方块加载 / 卸载 / 拆除时发生；仓每秒查一次，N 个仓就是 N 次哈希查找，
 * 没有扫描。UUID 用 {@link java.util.concurrent.ThreadLocalRandom} 生成，不走 SecureRandom（避免熵池卡顿）。
 */
public final class BeamVoidStorageRegistry {

    private static final Map<UUID, MTEBeamVoidStorage> LOADED = new HashMap<>();

    private BeamVoidStorageRegistry() {}

    /**
     * 注册。若同一 UUID 已经被<b>另一个还活着的</b>存储器占用（NBT 被复制 / 作弊刷物品），
     * 给新方块换一个新 UUID 并记一条警告 —— 否则两个存储会抢同一批仓。
     */
    public static UUID register(MTEBeamVoidStorage storage) {
        if (storage == null) return null;
        UUID id = storage.getStorageId();
        final MTEBeamVoidStorage existing = LOADED.get(id);
        if (existing == storage) return id;
        if (existing != null && isValid(existing)) {
            id = storage.regenerateStorageId();
            FMLLog.warning("[ElinarCurios] duplicate storage UUID " + id + " - re-rolled the new block");
        }
        LOADED.put(id, storage);
        return id;
    }

    public static void unregister(MTEBeamVoidStorage storage) {
        if (storage == null) return;
        final UUID id = storage.getStorageId();
        if (id != null) LOADED.remove(id, storage);
    }

    /** 按 UUID 找已加载的存储器；顺手剔除已经不成立的条目（防内存泄漏）。 */
    public static MTEBeamVoidStorage find(UUID id) {
        if (id == null) return null;
        final MTEBeamVoidStorage storage = LOADED.get(id);
        if (storage == null) return null;
        if (!isValid(storage)) {
            LOADED.remove(id, storage);
            return null;
        }
        return storage;
    }

    public static MTEBeamVoidStorage owner(UUID id) {
        return id == null ? null : LOADED.get(id);
    }

    public static void clear() {
        LOADED.clear();
    }

    /** 某个世界卸载（退出存档 / 换维度）时清掉属于它的条目。 */
    public static void clearWorld(World world) {
        final Iterator<Map.Entry<UUID, MTEBeamVoidStorage>> it = LOADED.entrySet()
            .iterator();
        while (it.hasNext()) {
            final MTEBeamVoidStorage storage = it.next()
                .getValue();
            final IGregTechTileEntity base = storage == null ? null : storage.getBaseMetaTileEntity();
            if (base == null || base.getWorld() == world) it.remove();
        }
    }

    /**
     * 条目还有效吗：有方块实体、方块实体所在区块已加载、那个位置上的 TE 就是它。
     * 区块未加载时返回 false（不为了校验去加载区块）——等它重新加载时 onFirstTick 会再注册。
     */
    private static boolean isValid(MTEBeamVoidStorage storage) {
        final IGregTechTileEntity base;
        try {
            base = storage.getBaseMetaTileEntity();
        } catch (Throwable ignored) {
            return false;
        }
        if (base == null) return false;
        final World world = base.getWorld();
        if (world == null) return false;
        final int x = base.getXCoord();
        final int y = base.getYCoord();
        final int z = base.getZCoord();
        if (!world.blockExists(x, y, z)) return false;
        final TileEntity te = world.getTileEntity(x, y, z);
        return te == base;
    }
}
