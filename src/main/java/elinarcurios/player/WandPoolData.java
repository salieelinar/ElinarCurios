package elinarcurios.player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.WorldServer;

import elinarcurios.util.WandPoolConstants;

/**
 * World-level database of shared wand pools, like an external "infinite storage" keyed by player UUID.
 *
 * <p>
 * Unlike the old per-player implementation this does <b>not</b> touch player NBT at all:
 * <ul>
 * <li>works for offline owners (machines keep consuming their pool after they log off);</li>
 * <li>supports <b>any</b> Thaumcraft aspect, not just the six primals — the pool is a
 * {@code tag -> long} map, so later mechanics can store compound/custom aspects too;</li>
 * <li>is saved with the world through {@link WorldSavedData} (no bespoke I/O, no flush thread).</li>
 * </ul>
 *
 * <p>
 * Format: {@code players: [ {id: <uuid>, vis: [ {k: <aspect tag>, v: <long>}, ... ]}, ... ]} —
 * tag lists instead of a compound keyed by aspect keep arbitrary aspect tags safe.
 */
public final class WandPoolData extends WorldSavedData {

    public static final String NAME = "elinarcurios_wandPools";

    private static final String TAG_PLAYERS = "players";
    private static final String TAG_ID = "id";
    private static final String TAG_VIS = "vis";
    private static final String TAG_KEY = "k";
    private static final String TAG_VALUE = "v";

    private final Map<UUID, Map<String, Long>> pools = new HashMap<UUID, Map<String, Long>>();

    public WandPoolData() {
        super(NAME);
    }

    public WandPoolData(final String name) {
        super(name);
    }

    /** World-wide instance stored on the overworld's map storage (shared by every dimension). */
    public static WandPoolData get() {
        final MinecraftServer server = MinecraftServer.getServer();
        if (server == null) return null;
        final WorldServer overworld = server.worldServerForDimension(0);
        if (overworld == null || overworld.mapStorage == null) return null;
        WandPoolData data = (WandPoolData) overworld.mapStorage.loadData(WandPoolData.class, NAME);
        if (data == null) {
            data = new WandPoolData(NAME);
            overworld.mapStorage.setData(NAME, data);
        }
        return data;
    }

    public long get(final UUID id, final String tag) {
        if (id == null || tag == null) return 0L;
        final Map<String, Long> pool = pools.get(id);
        if (pool == null) return 0L;
        final Long value = pool.get(tag);
        return value == null ? 0L : value;
    }

    public boolean has(final UUID id) {
        return id != null && pools.containsKey(id);
    }

    /** @return true when the value actually changed (so the caller can mark the owner dirty for sync). */
    public boolean set(final UUID id, final String tag, final long value) {
        if (id == null || tag == null) return false;
        final long next = clamp(value);
        final Map<String, Long> pool = pools.get(id);
        final long old = pool == null ? 0L : pool.getOrDefault(tag, 0L);
        if (old == next) return false;

        if (next <= 0L) {
            if (pool != null) {
                pool.remove(tag);
                if (pool.isEmpty()) pools.remove(id);
            }
        } else {
            Map<String, Long> target = pool;
            if (target == null) {
                target = new HashMap<String, Long>();
                pools.put(id, target);
            }
            target.put(tag, next);
        }
        markDirty();
        return true;
    }

    /** Immutable copy of one player's non-zero aspects (empty when the player has no pool yet). */
    public Map<String, Long> snapshot(final UUID id) {
        final Map<String, Long> pool = id == null ? null : pools.get(id);
        return pool == null ? new HashMap<String, Long>() : new HashMap<String, Long>(pool);
    }

    @Override
    public void readFromNBT(final NBTTagCompound nbt) {
        pools.clear();
        final NBTTagList players = nbt.getTagList(TAG_PLAYERS, 10);
        for (int i = 0; i < players.tagCount(); i++) {
            final NBTTagCompound playerTag = players.getCompoundTagAt(i);
            final UUID id;
            try {
                id = UUID.fromString(playerTag.getString(TAG_ID));
            } catch (final IllegalArgumentException ignored) {
                continue;
            }
            final Map<String, Long> pool = new HashMap<String, Long>();
            final NBTTagList vis = playerTag.getTagList(TAG_VIS, 10);
            for (int j = 0; j < vis.tagCount(); j++) {
                final NBTTagCompound entry = vis.getCompoundTagAt(j);
                final long value = clamp(entry.getLong(TAG_VALUE));
                if (value > 0L) pool.put(entry.getString(TAG_KEY), value);
            }
            if (!pool.isEmpty()) pools.put(id, pool);
        }
    }

    @Override
    public void writeToNBT(final NBTTagCompound nbt) {
        final NBTTagList players = new NBTTagList();
        for (final Map.Entry<UUID, Map<String, Long>> player : pools.entrySet()) {
            if (player.getValue()
                .isEmpty()) continue;
            final NBTTagCompound playerTag = new NBTTagCompound();
            playerTag.setString(
                TAG_ID,
                player.getKey()
                    .toString());
            final NBTTagList vis = new NBTTagList();
            for (final Map.Entry<String, Long> entry : player.getValue()
                .entrySet()) {
                if (entry.getValue() == null || entry.getValue() <= 0L) continue;
                final NBTTagCompound aspectTag = new NBTTagCompound();
                aspectTag.setString(TAG_KEY, entry.getKey());
                aspectTag.setLong(TAG_VALUE, entry.getValue());
                vis.appendTag(aspectTag);
            }
            playerTag.setTag(TAG_VIS, vis);
            players.appendTag(playerTag);
        }
        nbt.setTag(TAG_PLAYERS, players);
    }

    private static long clamp(final long value) {
        if (value <= 0L) return 0L;
        return value >= WandPoolConstants.CAPACITY ? WandPoolConstants.CAPACITY : value;
    }
}
